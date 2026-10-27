package services.system

import cats.syntax.all.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json,parser}
import java.net.URI
import java.net.http.{HttpClient,HttpRequest,HttpResponse}
import java.nio.file.{Files,Paths}
import java.util.UUID
import munit.FunSuite
import services.system.api.*
import services.accounts.tables.LocalIdentityTable.LocalIdentityTableInitializer
import services.cases.api.ImportCaseMessage

class WebInvestigationSuite extends FunSuite:
  private val client = HttpClient.newHttpClient()
  private def setup(url: String): IO[Unit] = DatabaseSession.connection(url).use { c =>
    DatabaseSchema.initialize(c) *> LocalIdentityTableInitializer.initialize(c) *>
      DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)).void
  }
  private def send(port: Int,name: String,body: String = "{}",cookie: Option[String] = None,origin: Option[String] = None): IO[(Int,Json,Option[String])] = IO.blocking {
    val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/api/$name"))
      .header("Content-Type","application/json").header("X-Detective-Client","web")
    cookie.foreach(request.header("Cookie",_))
    origin.foreach(request.header("Origin",_))
    val response = client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString())
    val setCookie = response.headers.firstValue("Set-Cookie")
    (response.statusCode,parser.parse(response.body).toOption.get,if setCookie.isPresent then Some(setCookie.get.split(';').head) else None)
  }
  private def withServer(test: Int => IO[Unit]): Unit =
    val url = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
    (setup(url) *> LocalHttpServer.resource(url,0).use(server => test(server.getAddress.getPort))).unsafeRunSync()
  private def payload(id: String) = s"""{"sessionId":"$id"}"""
  private def execute(id: String,request: String="open",seq: Long=0) =
    s"""{"sessionId":"$id","requestId":"$request","expectedSequence":{"value":$seq},"operation":"open_drawer"}"""
  private def investigate(id: String,request: String="read",seq: Long=1) =
    s"""{"sessionId":"$id","requestId":"$request","expectedSequence":{"value":$seq},"point":"point_letter","route":"route_letter"}"""

  test("HTTP projection hides unrevealed content and start retry preserves the session") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      id = UUID.randomUUID().toString
      initial <- send(port,"openstudy",payload(id),identity._3)
      _ = assertEquals(initial._1,200)
      _ = assert(!initial._2.noSpaces.contains("今晚"))
      _ = assert(!initial._2.noSpaces.contains("route_letter"))
      _ = assertEquals(initial._2.hcursor.downField("clues").focus.get.asArray.get.size,0)
      _ = assertEquals(initial._2.hcursor.downField("scene").get[Boolean]("letterVisible"),Right(false))
      _ <- send(port,"executeoperation",execute(id),identity._3)
      resumed <- send(port,"openstudy",payload(id),identity._3)
      _ = assertEquals(resumed._2.hcursor.downField("sequence").get[Long]("value"),Right(1L))
    yield () }
  }
  test("HTTP rejects missing credentials, impersonation, foreign sessions and cross-origin requests") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      other <- send(port,"openlocalidentity")
      id = UUID.randomUUID().toString
      _ <- send(port,"openstudy",payload(id),identity._3)
      missing <- send(port,"getinvestigationsnapshot",payload(id))
      foreign <- send(port,"getinvestigationsnapshot",payload(id),other._3)
      forged <- send(port,"getinvestigationsnapshot",s"""{"sessionId":"$id","userId":"victim"}""",identity._3)
      cross <- send(port,"openlocalidentity",origin=Some("https://evil.example"))
      unsafeNumber <- send(port,"executeoperation",execute(id,seq=9007199254740992L),identity._3)
      malformed <- send(port,"openlocalidentity","not json")
      _ = assertEquals(Vector(missing._1,foreign._1,forged._1,cross._1,unsafeNumber._1,malformed._1),Vector(401,403,400,403,400,400))
      _ = assert(!missing._2.noSpaces.contains("token_hash"))
    yield () }
  }
  test("HTTP mutations are idempotent, stale requests fail and snapshot stays consistent") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      id = UUID.randomUUID().toString
      _ <- send(port,"openstudy",payload(id),identity._3)
      hidden <- send(port,"investigate",investigate(id,seq=0),identity._3)
      opened <- send(port,"executeoperation",execute(id),identity._3)
      learned <- send(port,"investigate",investigate(id),identity._3)
      retry <- send(port,"investigate",investigate(id),identity._3)
      conflict <- send(port,"investigate",investigate(id,seq=2),identity._3)
      stale <- send(port,"investigate",investigate(id,"stale",0),identity._3)
      snapshot <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
      _ = assertEquals(hidden._1,403)
      _ = assertEquals(opened._1,200)
      _ = assertEquals(learned._1,200)
      _ = assertEquals(retry._2,learned._2)
      _ = assertEquals(conflict._1,409)
      _ = assertEquals(stale._1,409)
      _ = assert(learned._2.hcursor.downField("result").focus.isEmpty)
      _ = assertEquals(snapshot._2.hcursor.downField("sequence").get[Long]("value"),Right(2L))
      _ = assertEquals(snapshot._2.hcursor.downField("journal").focus.get.asArray.get.size,2)
      _ = assert(snapshot._2.noSpaces.contains("今晚九点见"))
    yield () }
  }
  test("file database restores local credentials and full player view after HTTP server restart") {
    val directory = Files.createTempDirectory("detective-web-")
    val url = s"jdbc:h2:file:${directory.resolve("case")};LOCK_TIMEOUT=10000"
    val id = UUID.randomUUID().toString
    (for
      _ <- setup(url)
      saved <- LocalHttpServer.resource(url,0).use { server =>
        val port = server.getAddress.getPort
        for
          identity <- send(port,"openlocalidentity")
          _ <- send(port,"openstudy",payload(id),identity._3)
          _ <- send(port,"executeoperation",execute(id),identity._3)
          _ <- send(port,"investigate",investigate(id),identity._3)
          _ <- send(port,"executeoperation",execute(id,"close-before-restart",2).replace("open_drawer","close_drawer"),identity._3)
          snapshot <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
        yield (identity._3,snapshot._2)
      }
      _ <- LocalHttpServer.resource(url,0).use { server => for
        identity <- send(server.getAddress.getPort,"openlocalidentity",cookie=saved._1)
        restored <- send(server.getAddress.getPort,"getinvestigationsnapshot",payload(id),identity._3)
        retry <- send(server.getAddress.getPort,"investigate",investigate(id),identity._3)
        _ = assertEquals(identity._3,saved._1)
        _ = assertEquals(restored._1,200)
        _ = assertEquals(restored._2,saved._2)
        _ = assertEquals(retry._1,200)
      yield () }
    yield ()).unsafeRunSync()
  }

  test("concurrent creation retries return one owned session") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      id = UUID.randomUUID().toString
      replies <- Vector(1,2).parTraverse(_ => send(port,"openstudy",payload(id),identity._3))
      _ = assertEquals(replies.map(_._1),Vector(200,200))
      _ = assertEquals(replies.head._2,replies.last._2)
    yield () }
  }

  test("reversible drawer keeps acquired knowledge and rejects unavailable actions") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      id = UUID.randomUUID().toString
      initial <- send(port,"openstudy",payload(id),identity._3)
      _ = assertEquals(initial._2.hcursor.downField("observations").focus.get.asArray.get.size,4)
      badClose <- send(port,"executeoperation",execute(id,"bad-close",0).replace("open_drawer","close_drawer"),identity._3)
      _ = assertEquals(badClose._1,409)
      _ <- send(port,"executeoperation",execute(id),identity._3)
      _ <- send(port,"investigate",investigate(id),identity._3)
      closePayload = execute(id,"close",2).replace("open_drawer","close_drawer")
      closed <- send(port,"executeoperation",closePayload,identity._3)
      retry <- send(port,"executeoperation",closePayload,identity._3)
      afterClose <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
      _ = assertEquals(closed._1,200)
      _ = assertEquals(retry._2,closed._2)
      _ = assertEquals(afterClose._2.hcursor.downField("scene").get[Boolean]("drawerOpened"),Right(false))
      _ = assertEquals(afterClose._2.hcursor.downField("scene").get[Boolean]("letterVisible"),Right(false))
      _ = assertEquals(afterClose._2.hcursor.downField("clues").focus.get.asArray.get.size,1)
      hidden <- send(port,"investigate",investigate(id,"hidden",3),identity._3)
      _ = assertEquals(hidden._1,403)
      reopened <- send(port,"executeoperation",execute(id,"reopen",3),identity._3)
      _ = assertEquals(reopened._1,200)
      repeatRead <- send(port,"investigate",investigate(id,"already-known",4),identity._3)
      _ = assertEquals(repeatRead._1,409)
      _ <- send(port,"executeoperation",execute(id,"close-again",4).replace("open_drawer","close_drawer"),identity._3)
      finalState <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
      _ = assertEquals(finalState._2.hcursor.downField("sequence").get[Long]("value"),Right(5L))
      _ = assertEquals(finalState._2.hcursor.downField("journal").focus.get.asArray.get.size,5)
      _ = assertEquals(finalState._2.hcursor.downField("clues").focus.get.asArray.get.size,1)
    yield () }
  }
