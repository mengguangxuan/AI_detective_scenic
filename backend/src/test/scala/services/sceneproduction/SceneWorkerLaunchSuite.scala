package services.sceneproduction
import cats.effect.{IO,Deferred}
import cats.effect.unsafe.implicits.global
import io.circe.{Json,parser}
import java.nio.file.{Files,Paths}
import java.util.UUID
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import munit.FunSuite
import services.models.api.ChatCompletionClient
import services.models.objects.{ChatCompletionInput,ModelRequestFailure,ModelFailureCode}
import services.sceneproduction.api.SceneProductionWorker
import services.sceneproduction.objects.*
import services.system.objects.UserId

// Real backend -> Node -> private bridge -> fake model. No supplier or user task calls.
class SceneWorkerLaunchSuite extends FunSuite:
  test("native worker startup and reconnect preserve planning without Windows command-line metadata") {
    val project=Paths.get("..").toAbsolutePath.normalize()
    val id=SceneProductionId(UUID.randomUUID().toString)
    val folder=project.resolve("backend/.build-cache/scene-production").resolve(id.value)
    val fields=Json.obj("original"->Json.fromString("generic worker integration test"))
    val row=SceneProductionRecord(id,UserId("test-author"),SceneAuthorInput("启动测试","room",fields),SceneProductionPhase.Queued,"now",None)
    val plan="""{"inputSufficient":true,"gaps":[],"design":{"title":"Test layout","decisions":[]},"modules":[{"id":"first","task":"generic test","specs":[{"parameters":{},"sizeBudget":{"widthMeters":1,"heightMeters":1,"depthMeters":1}}]},{"id":"second","task":"generic test","specs":[{"parameters":{},"sizeBudget":{"widthMeters":1,"heightMeters":1,"depthMeters":1}}]}],"instances":[{"id":"part","module":"first","specIndex":0,"placement":{"positionMeters":{"x":0,"y":0,"z":0},"rotation":{"xRad":0,"yRad":0,"zRad":0,"order":"XYZ"},"scale":{"x":1,"y":1,"z":1}}}]}"""
    def cleanup: IO[Unit]=IO.blocking {
      val launch=folder.resolve("launch.json")
      if Files.exists(launch) then
        val pid=parser.parse(Files.readString(launch)).toOption.get.hcursor.get[Long]("pid").toOption.get
        val process=ProcessHandle.of(pid)
        if process.isPresent && process.get().isAlive then
          process.get().destroy()
          process.get().onExit().get(5,java.util.concurrent.TimeUnit.SECONDS)
      if Files.exists(folder) then
        val paths=Files.walk(folder)
        try paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
        finally paths.close()
    }
    (for
      entered <- Deferred[IO,Unit]
      release <- Deferred[IO,Unit]
      model=new ChatCompletionClient:
        def complete(input: ChatCompletionInput): IO[String]=
          IO { assertEquals(parser.parse(input.user).toOption.get.hcursor.downField("rawInput").downField("sourceFields").focus,Some(fields)) } *>
            (if parser.parse(input.user).toOption.get.hcursor.downField("module").succeeded then IO.raiseError(ModelRequestFailure(ModelFailureCode.ProviderHttpError,Some(429)))
             else entered.complete(()).void *> release.get.as(plan))
      _ <- SceneProductionWorker.managed(model,project).use { worker =>
        def awaitLayout(n: Int): IO[SceneProductionStatus]=worker.status(row).flatMap { status =>
          if status.stage=="awaiting_layout" || n==0 then IO.pure(status)
          else IO.sleep(100.millis) *> awaitLayout(n-1)
        }
        def awaitFailure(n: Int): IO[SceneProductionStatus]=worker.status(row).flatMap { status =>
          if status.stage=="failed" || n==0 then IO.pure(status)
          else IO.sleep(100.millis) *> awaitFailure(n-1)
        }
        for
          _ <- worker.start(row)
          initial <- worker.status(row)
          _ = assertNotEquals(initial.stage,"failed")
          _ <- entered.get.timeout(15.seconds)
          reconnected <- SceneProductionWorker.local(project).status(row)
          _ = assertEquals(reconnected.stage,"planning")
          _ <- release.complete(())
          ready <- awaitLayout(100)
          _ = assertEquals(ready.stage,"awaiting_layout")
          _ = assertEquals(ready.calls,1)
          _ <- worker.start(row)
          duplicate <- worker.status(row)
          _ = assertEquals(duplicate.calls,1)
          _ <- worker.confirmLayout(row)
          failed <- awaitFailure(100)
          _ = assertEquals(failed.calls,2)
          _ <- worker.resume(row,2)
          resumed <- awaitFailure(100)
          _ = assertEquals(resumed.calls,3)
          _ <- worker.resume(row,2)
          same <- worker.status(row)
          _ = assertEquals(same.calls,3)
        yield ()
      }
    yield ()).guarantee(cleanup).unsafeRunSync()
  }
