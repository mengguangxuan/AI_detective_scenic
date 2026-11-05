package services.sceneproduction
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json,parser}
import java.nio.file.Files
import munit.FunSuite
import services.models.api.ChatCompletionClient
import services.models.objects.{ChatCompletionInput,ModelRequestFailure,ModelFailureCode}
import services.sceneproduction.api.DiagnoseSceneRequest
class SceneDiagnosticSuite extends FunSuite:
  test("one explicit text diagnosis preserves the failed task, accounts usage, saves partial output and cannot repeat") {
    val folder=Files.createTempDirectory("scene-diagnosis-")
    Files.createDirectories(folder.resolve("requests"))
    Files.writeString(folder.resolve("status.json"),"""{"stage":"failed","calls":2,"tokens":10,"lastError":"original failure"}""")
    val request=folder.resolve("requests/02-module-test.request.json")
    Files.writeString(request,"""{"max_tokens":4500,"messages":[{"role":"system","content":"generic"},{"role":"user","content":"raw source"}]}""")
    var calls=0
    val model=new ChatCompletionClient:
      def complete(input: ChatCompletionInput): IO[String]=IO {calls+=1;assertEquals(input.user,"raw source")} *> IO.raiseError(ModelRequestFailure(ModelFailureCode.IncompleteOutput,Some(200),Some("length"),Some(20),Some(4),17,Some(4500),Some("code")))
    DiagnoseSceneRequest.diagnose(folder,request,model).unsafeRunSync()
    val status=parser.parse(Files.readString(folder.resolve("status.json"))).toOption.get
    assertEquals(status.hcursor.get[String]("stage"),Right("failed"))
    assertEquals(status.hcursor.get[String]("lastError"),Right("original failure"))
    assertEquals(status.hcursor.get[Int]("calls"),Right(3))
    assertEquals(status.hcursor.get[Long]("tokens"),Right(27L))
    assert(!status.noSpaces.contains("partialContent"))
    assert(Files.readString(folder.resolve("requests/03-diagnostic.failure.json")).contains("partialContent"))
    assert(DiagnoseSceneRequest.diagnose(folder,request,model).attempt.unsafeRunSync().isLeft)
    assertEquals(calls,1)
  }
