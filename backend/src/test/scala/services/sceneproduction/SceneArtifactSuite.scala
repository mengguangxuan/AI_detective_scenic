package services.sceneproduction
import cats.effect.unsafe.implicits.global
import io.circe.Json
import java.nio.file.Files
import java.util.UUID
import munit.FunSuite
import services.sceneproduction.api.SceneProductionWorker
import services.sceneproduction.objects.*
import services.system.objects.UserId

class SceneArtifactSuite extends FunSuite:
  test("only complete accepted scenes are saved privately; repeat saves retain the same artifact") {
    val project=Files.createTempDirectory("scene-artifact-")
    val id=SceneProductionId(UUID.randomUUID().toString)
    val row=SceneProductionRecord(id,UserId("author"),SceneAuthorInput("测试","room",Json.obj("raw"->Json.fromString("source"))),SceneProductionPhase.Queued,"now",None)
    val worker=SceneProductionWorker.local(project)
    val job=project.resolve("backend/.build-cache/scene-production").resolve(id.value)
    Files.createDirectories(job.resolve("modules"))
    def write(name: String,text: String): Unit = { Files.writeString(job.resolve(name),text);() }
    write("status.json","""{"stage":"passed","reviews":{"visual":{"passed":true},"overall":{"passed":false}}}""")
    write("program-checks.json","""{"passed":true}""")
    assert(worker.save(row).attempt.unsafeRunSync().isLeft)
    assertEquals(worker.status(row).unsafeRunSync().stage,"failed")
    write("status.json","""{"stage":"passed","reviews":{"visual":{"passed":true},"overall":{"passed":true}}}""")
    write("plan.json","""{"modules":[{"id":"sample"}]}""")
    write("input.json","{}");write("assembler.js","export function assembleScene(){}");write("modules/sample.js","export function buildAsset(){}")
    write("access.json","""{"token":"never-copy"}""")
    worker.save(row).unsafeRunSync()
    val saved=project.resolve("backend/target/scene-artifacts").resolve(id.value)
    assert(Files.exists(saved.resolve("modules/sample.js")))
    assert(!Files.exists(saved.resolve("access.json")))
    write("modules/sample.js","changed after save")
    worker.save(row).unsafeRunSync()
    assertEquals(Files.readString(saved.resolve("modules/sample.js")),"export function buildAsset(){}")
  }
