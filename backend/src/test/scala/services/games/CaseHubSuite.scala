package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Paths
import java.util.UUID
import munit.FunSuite
import services.accounts.tables.LocalIdentityTable.LocalIdentityTableInitializer
import services.cases.api.{GetCaseCatalogMessage, ImportCaseMessage}
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.api.*
import services.system.api.*
import services.system.objects.*

class CaseHubSuite extends FunSuite:
  private val client=HttpClient.newHttpClient()
  private val first=CaseId("vicarage_study")
  private val second=CaseId("second_study")
  private val revision=Revision(21)
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def release(id: CaseId)=CaseReleaseRecord(id,revision,"公开介绍，仅供选案。",Vector("推理"),"study","5")
  private def setup(url: String)=DatabaseSession.connection(url).use { c =>
    DatabaseSchema.initialize(c) *> LocalIdentityTableInitializer.initialize(c) *> DatabaseSession.transaction(c)(for
      current <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
      _ <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation_v20.json")).plan(c)
      _ <- CaseRevisionTable.insert(c,current.copy(caseId=second,title="第二个书房模板样例"))
      _ <- RegisterPlayableCaseMessage(release(first)).plan(c)
    yield ())
  }
  private def send(port: Int,name: String,payload: Json=Json.obj(),cookie: Option[String]=None): IO[(Int,Json,Option[String])]=IO.blocking {
    val request=HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/api/$name"))
      .header("Content-Type","application/json").header("X-Detective-Client","web")
    cookie.foreach(request.header("Cookie",_))
    val reply=client.send(request.POST(HttpRequest.BodyPublishers.ofString(payload.noSpaces)).build(),HttpResponse.BodyHandlers.ofString())
    val token=reply.headers.firstValue("Set-Cookie")
    (reply.statusCode,parser.parse(reply.body).toOption.get,if token.isPresent then Some(token.get.split(';').head) else None)
  }
  private def open(id: String,caseId: CaseId=first)=Json.obj("sessionId" -> id.asJson,"caseId" -> caseId.asJson,"revision" -> revision.asJson)
  private def session(id: String)=Json.obj("sessionId" -> id.asJson)

  test("catalogue is public, only lists published metadata, and browsing never creates a game") {
    val url=database
    (setup(url) *> LocalHttpServer.resource(url,0).use { server =>
      val port=server.getAddress.getPort
      for
        catalog <- send(port,"getcasecatalog")
        _ = assertEquals(catalog._1,200)
        _ = assertEquals(catalog._2.asArray.get.size,1)
        _ = assert(!catalog._2.noSpaces.contains("authorTruth"))
        _ = assert(!catalog._2.noSpaces.contains("privateContext"))
        denied <- send(port,"listinvestigations")
        _ = assertEquals(denied._1,401)
        identity <- send(port,"openlocalidentity")
        before <- send(port,"listinvestigations",cookie=identity._3)
        _ <- send(port,"getcasecatalog")
        after <- send(port,"listinvestigations",cookie=identity._3)
        _ = assertEquals(before._2,Json.arr())
        _ = assertEquals(after._2,before._2)
        spoofed <- send(port,"listinvestigations",Json.obj("userId" -> "someone-else".asJson),identity._3)
        _ = assertEquals(spoofed._1,400)
      yield ()
    }).unsafeRunSync()
  }

  test("explicit creation is retryable, enforces publication and ownership, and binds the exact requested case") {
    val url=database;val id=UUID.randomUUID().toString
    (setup(url) *> LocalHttpServer.resource(url,0).use { server =>
      val port=server.getAddress.getPort
      for
        identity <- send(port,"openlocalidentity")
        hidden <- send(port,"openinvestigation",open(UUID.randomUUID().toString,second),identity._3)
        _ = assertEquals(hidden._1,403)
        concurrent <- send(port,"openinvestigation",open(id),identity._3).both(send(port,"openinvestigation",open(id),identity._3))
        _ = assertEquals(concurrent._1._1,200)
        _ = assertEquals(concurrent._1._2,concurrent._2._2)
        listing <- send(port,"listinvestigations",cookie=identity._3)
        _ = assertEquals(listing._2.asArray.get.size,1)
        _ <- tx(url)(RegisterPlayableCaseMessage(release(second)).plan)
        changed <- send(port,"openinvestigation",open(id,second),identity._3)
        _ = assertEquals(changed._1,409)
        other <- send(port,"openlocalidentity")
        foreign <- send(port,"openinvestigation",open(id),other._3)
        empty <- send(port,"listinvestigations",cookie=other._3)
        _ = assertEquals(foreign._1,403)
        _ = assertEquals(empty._2,Json.arr())
      yield ()
    }).unsafeRunSync()
  }

  test("a second registered case uses the same renderer with isolated progress and read-only listing") {
    val url=database;val one=UUID.randomUUID().toString;val two=UUID.randomUUID().toString
    (setup(url) *> tx(url)(RegisterPlayableCaseMessage(release(second)).plan) *> LocalHttpServer.resource(url,0).use { server =>
      val port=server.getAddress.getPort
      for
        identity <- send(port,"openlocalidentity")
        _ <- send(port,"openinvestigation",open(one),identity._3)
        _ <- send(port,"investigate",Json.obj("sessionId" -> one.asJson,"requestId" -> "read-clock".asJson,
          "expectedSequence" -> Sequence(0).asJson,"point" -> "point_clock".asJson,"route" -> "route_clock".asJson),identity._3)
        opened <- send(port,"openinvestigation",open(two,second),identity._3)
        _ = assertEquals(opened._1,200)
        _ = assertEquals(opened._2.hcursor.get[String]("caseId"),Right(second.value))
        _ = assertEquals(opened._2.hcursor.get[Vector[Json]]("clues"),Right(Vector.empty))
        before <- send(port,"getinvestigationsnapshot",session(one),identity._3)
        listing <- send(port,"listinvestigations",cookie=identity._3)
        again <- send(port,"listinvestigations",cookie=identity._3)
        after <- send(port,"getinvestigationsnapshot",session(one),identity._3)
        _ = assertEquals(before._2,after._2)
        _ = assertEquals(listing._2,again._2)
        _ = assertEquals(listing._2.asArray.get.size,2)
        _ = assert(listing._2.asArray.get.forall(_.hcursor.get[Long]("updatedAtMillis").exists(_ > 0)))
        _ = assert(!listing._2.noSpaces.contains("18:22"))
        _ <- send(port,"revealreasoning",Json.obj("sessionId" -> two.asJson,"requestId" -> "reveal".asJson,
          "expectedSequence" -> Sequence(0).asJson,"abandon" -> true.asJson),identity._3)
        ended <- send(port,"listinvestigations",cookie=identity._3)
        _ = assert(ended._2.asArray.get.exists(_.hcursor.get[String]("status") == Right("Revealed")))
        _ = assert(!ended._2.noSpaces.contains("作者复盘"))
      yield ()
    }).unsafeRunSync()
  }

  test("publication refuses unsupported scenes and cannot overwrite a released revision") {
    val url=database
    (for
      _ <- setup(url)
      unsupported <- tx(url)(RegisterPlayableCaseMessage(release(second).copy(sceneBundle="unregistered-scene")).plan).attempt
      overwrite <- tx(url)(RegisterPlayableCaseMessage(release(first).copy(summary="改写已发布介绍")).plan).attempt
      _ = assert(unsupported.isLeft)
      _ = assert(overwrite.isLeft)
      catalog <- tx(url)(GetCaseCatalogMessage().plan)
      _ = assertEquals(catalog.map(_.caseId),Vector(first))
    yield ()).unsafeRunSync()
  }

  test("a pre-activity-column investigation survives schema upgrade without a fabricated activity time") {
    val url=database;val owner=UserId("old-owner");val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url)
      before <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ <- DatabaseSession.connection(url).use(c => IO.blocking {
        val statement=c.createStatement()
        try statement.execute("ALTER TABLE game_sessions DROP COLUMN updated_at_ms") finally statement.close()
      })
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      listing <- tx(url)(ListInvestigationsMessage(owner).plan)
      after <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(listing.size,1)
      _ = assertEquals(listing.head.updatedAtMillis,None)
      _ = assertEquals(after,before)
    yield ()).unsafeRunSync()
  }
