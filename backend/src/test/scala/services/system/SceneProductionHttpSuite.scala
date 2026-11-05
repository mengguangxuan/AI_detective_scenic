package services.system

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json,parser}
import io.circe.syntax.*
import java.net.URI
import java.net.http.{HttpClient,HttpRequest,HttpResponse}
import java.nio.file.Files
import java.util.UUID
import munit.FunSuite
import services.sceneproduction.api.SceneProductionWorker
import services.sceneproduction.objects.*
import services.system.api.*
import services.accounts.tables.LocalIdentityTable.LocalIdentityTableInitializer
import services.system.objects.*

class SceneProductionHttpSuite extends FunSuite:
  private val client=HttpClient.newHttpClient()
  private class Worker extends SceneProductionWorker:
    var starts=Set.empty[String]
    var resumes=0
    override def resume(row: SceneProductionRecord,expectedCalls: Int): IO[Unit]=IO {resumes+=1}
    var confirmed=false
    var passed=false
    def start(row: SceneProductionRecord): IO[Unit] = IO { starts=starts+row.id.value }
    def status(row: SceneProductionRecord): IO[SceneProductionStatus] = IO.pure(SceneProductionStatus(if row.phase==SceneProductionPhase.Draft then "draft" else if passed then "passed" else "awaiting_layout","测试状态","",0,10,0,0,SceneProductionProgress(0,0,0,0,0),Vector.empty))
    def confirmLayout(row: SceneProductionRecord): IO[Unit] = IO { confirmed=true }
    def save(row: SceneProductionRecord): IO[Unit] = if passed then IO.unit else IO.raiseError(GameError(ErrorCode.ConditionFailed,"尚未验收"))
    def preview(row: SceneProductionRecord): IO[Option[String]] = IO.pure(None)
  private def send(port: Int,name: String,body: Json=Json.obj(),cookie: Option[String]=None): IO[(Int,Json,Option[String])] = IO.blocking {
    val request=HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/api/$name")).header("Content-Type","application/json").header("X-Detective-Client","web")
    cookie.foreach(request.header("Cookie",_))
    val response=client.send(request.POST(HttpRequest.BodyPublishers.ofString(body.noSpaces)).build(),HttpResponse.BodyHandlers.ofString())
    val credential=response.headers.firstValue("Set-Cookie")
    (response.statusCode(),parser.parse(response.body()).toOption.get,if credential.isPresent then Some(credential.get.split(';').head) else None)
  }
  test("author fields survive reconnect; ownership, frozen inputs, layout confirmation and accepted save are enforced") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("scene-author-").resolve("case")};LOCK_TIMEOUT=10000"
    val worker=new Worker
    val id=UUID.randomUUID().toString
    val ref=Json.obj("productionId"->id.asJson)
    val input=SceneAuthorInput("作者制作","room",Json.obj("sourceText"->("原始文字"*3000).asJson,"custom"->Json.obj("nested"->Vector(1,2,3).asJson)))
    val body=Json.obj("productionId"->id.asJson,"input"->input.asJson)
    (for
      _ <- DatabaseSession.connection(url).use(c=>DatabaseSchema.initialize(c) *> LocalIdentityTableInitializer.initialize(c))
      snapshot <- LocalHttpServer.resource(url,0,sceneWorker=Some(worker)).use { server =>
        val port=server.getAddress.getPort
        for
          owner <- send(port,"openlocalidentity")
          other <- send(port,"openlocalidentity")
          draft <- send(port,"savescenedraft",body,owner._3)
          _ = assertEquals(draft._1,200)
          _ = assertEquals(draft._2.hcursor.downField("input").as[SceneAuthorInput],Right(input))
          missing <- send(port,"getsceneproduction",ref)
          foreign <- send(port,"startsceneproduction",ref,other._3)
          hidden <- send(port,"listsceneproductions",cookie=other._3)
          _ = assertEquals(Vector(missing._1,foreign._1),Vector(401,403))
          _ = assertEquals(hidden._2,Json.arr())
          _ = assertEquals(worker.starts.size,0)
          queued <- send(port,"startsceneproduction",ref,owner._3)
          retry <- send(port,"startsceneproduction",ref,owner._3)
          _ = assertEquals(queued._1,200)
          _ = assertEquals(retry._2,queued._2)
          _ = assertEquals(worker.starts.size,1)
          deniedResume <- send(port,"resumesceneproduction",ref.mapObject(_.add("expectedCalls",Json.fromInt(0))),other._3)
          _ = assertEquals(deniedResume._1,403)
          _ = assertEquals(worker.resumes,0)
          acceptedResume <- send(port,"resumesceneproduction",ref.mapObject(_.add("expectedCalls",Json.fromInt(0))),owner._3)
          _ = assertEquals(acceptedResume._1,200)
          _ = assertEquals(worker.resumes,1)
          changed <- send(port,"savescenedraft",body.mapObject(_.add("input",input.copy(title="新标题").asJson)),owner._3)
          same <- send(port,"savescenedraft",body,owner._3)
          _ = assertEquals(changed._1,409)
          _ = assertEquals(same._1,200)
          deniedConfirm <- send(port,"confirmscenelayout",ref,other._3)
          _ = assertEquals(deniedConfirm._1,403)
          _ = assert(!worker.confirmed)
          _ <- send(port,"confirmscenelayout",ref,owner._3)
          _ = assert(worker.confirmed)
          rejected <- send(port,"savesceneartifact",ref,owner._3)
          _ = assertEquals(rejected._1,409)
          _ = worker.passed=true
          saved <- send(port,"savesceneartifact",ref,owner._3)
          duplicate <- send(port,"savesceneartifact",ref,owner._3)
          _ = assertEquals(saved._1,200)
          _ = assertEquals(saved._2.hcursor.get[String]("phase"),Right("Saved"))
          _ = assertEquals(saved._2,duplicate._2)
          _ = assert(!saved._2.noSpaces.contains("owner"))
        yield (owner._3,saved._2)
      }
      _ <- LocalHttpServer.resource(url,0,sceneWorker=Some(worker)).use { server => for
        resumed <- send(server.getAddress.getPort,"getsceneproduction",ref,snapshot._1)
        _ = assertEquals(resumed._2,snapshot._2)
      yield () }
    yield ()).unsafeRunSync()
  }
