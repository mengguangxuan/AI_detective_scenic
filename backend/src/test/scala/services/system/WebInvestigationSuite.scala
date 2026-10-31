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
    s"""{"sessionId":"$id","requestId":"$request","expectedSequence":{"value":$seq},"point":"point_clock","route":"route_clock"}"""

  test("HTTP projection hides unrevealed content and start retry preserves the session") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      id = UUID.randomUUID().toString
      initial <- send(port,"openstudy",payload(id),identity._3)
      _ = assertEquals(initial._1,200)
      _ = assert(!initial._2.noSpaces.contains("今晚"))
      _ = assert(!initial._2.noSpaces.contains("route_clock_presented"))
      _ = assertEquals(initial._2.hcursor.downField("clues").focus.get.asArray.get.size,0)
      _ = assertEquals(initial._2.hcursor.downField("stage").get[Boolean]("completed"),Right(false))
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
      hidden <- send(port,"investigate",investigate(id,seq=0).replace("point_clock","point_clement").replace("route_clock","route_clock_presented"),identity._3)
      opened <- send(port,"executeoperation",execute(id),identity._3)
      learned <- send(port,"investigate",investigate(id),identity._3)
      retry <- send(port,"investigate",investigate(id),identity._3)
      conflict <- send(port,"investigate",investigate(id,seq=2),identity._3)
      stale <- send(port,"investigate",investigate(id,"stale",0),identity._3)
      snapshot <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
      _ = assertEquals(hidden._1,409)
      _ = assertEquals(opened._1,200)
      _ = assertEquals(learned._1,200)
      _ = assertEquals(retry._2,learned._2)
      _ = assertEquals(conflict._1,409)
      _ = assertEquals(stale._1,409)
      _ = assert(learned._2.hcursor.downField("result").focus.isEmpty)
      _ = assertEquals(snapshot._2.hcursor.downField("sequence").get[Long]("value"),Right(2L))
      _ = assertEquals(snapshot._2.hcursor.downField("journal").focus.get.asArray.get.size,2)
      _ = assert(snapshot._2.noSpaces.contains("18:22"))
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
      _ = assertEquals(initial._2.hcursor.downField("observations").focus.get.asArray.get.size,8)
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
      _ = assertEquals(afterClose._2.hcursor.downField("clues").focus.get.asArray.get.size,1)
      hidden <- send(port,"investigate",investigate(id,"hidden",3).replace("route_clock","route_clock_presented").replace("point_clock","point_clement"),identity._3)
      _ = assertEquals(hidden._1,409)
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


  private val requiredPath = Vector(
    "clock" -> "clock", "paper" -> "paper", "desk" -> "desk", "clement" -> "habit",
    "mary" -> "arrival", "mary" -> "arrival_basis")
  private def inquiry(id: String, point: String, route: String, seq: Long, request: String): String =
    s"""{"sessionId":"$id","requestId":"$request","expectedSequence":{"value":$seq},"point":"point_$point","route":"route_$route"}"""
  private def comparison(id: String, seq: Long = 6, request: String = "compare", minutes: Int = 1087,
      assessment: String = "conflict", evidence: String = "\"clue_clock\",\"clue_habit\",\"clue_arrival_basis\""): String =
    s"""{"sessionId":"$id","requestId":"$request","expectedSequence":{"value":$seq},"evidence":[$evidence],"convertedTimeMinutes":$minutes,"assessment":"$assessment"}"""

  test("player calculation and evidence complete the stage and survive an actual HTTP restart") {
    val directory = Files.createTempDirectory("vicarage-stage-")
    val url = s"jdbc:h2:file:${directory.resolve("case")};LOCK_TIMEOUT=10000"
    val id = UUID.randomUUID().toString
    (for
      _ <- setup(url)
      saved <- LocalHttpServer.resource(url,0).use { server =>
        val port = server.getAddress.getPort
        for
          identity <- send(port,"openlocalidentity")
          initial <- send(port,"openstudy",payload(id),identity._3)
          _ = assert(!initial._2.noSpaces.contains("18:22"))
          _ = assert(!initial._2.noSpaces.contains("18:07"))
          _ = assert(!initial._2.noSpaces.contains("convertedTimeMinutes"))
          early <- send(port,"comparetimes",comparison(id,seq=0),identity._3)
          oldShortcut <- send(port,"investigate",inquiry(id,"compare","time_contradiction",0,"old-shortcut"),identity._3)
          _ = assertEquals(early._1,403)
          _ = assertEquals(oldShortcut._1,403)
          _ <- requiredPath.zipWithIndex.traverse_ { case ((point,route),index) =>
            send(port,"investigate",inquiry(id,point,route,index.toLong,s"step-$index"),identity._3).map(reply => assertEquals(reply._1,200))
          }
          before <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
          _ = assertEquals(before._2.hcursor.downField("stage").get[Boolean]("completed"),Right(false))
          _ = assert(!before._2.noSpaces.contains("18:07"))
          wrongTime <- send(port,"comparetimes",comparison(id,request="wrong-time",minutes=1102),identity._3)
          wrongAssessment <- send(port,"comparetimes",comparison(id,request="wrong-assessment",assessment="consistent"),identity._3)
          missingBasis <- send(port,"comparetimes",comparison(id,request="missing-basis",evidence="\"clue_clock\",\"clue_habit\""),identity._3)
          invented <- send(port,"comparetimes",comparison(id,request="invented",evidence="\"clue_future_report\""),identity._3)
          _ = assertEquals(Vector(wrongTime._1,wrongAssessment._1,missingBasis._1,invented._1),Vector(409,409,409,403))
          _ = assert(!wrongTime._2.noSpaces.contains("18:07"))
          doneBody = comparison(id)
          done <- send(port,"comparetimes",doneBody,identity._3)
          retry <- send(port,"comparetimes",doneBody,identity._3)
          conflict <- send(port,"comparetimes",comparison(id,minutes=1102),identity._3)
          repeated <- send(port,"comparetimes",comparison(id,seq=7,request="again"),identity._3)
          _ = assertEquals(done._1,200)
          _ = assertEquals(retry._2,done._2)
          _ = assertEquals(Vector(conflict._1,repeated._1),Vector(409,409))
          snapshot <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
          _ = assertEquals(snapshot._2.hcursor.downField("stage").get[Boolean]("completed"),Right(true))
          _ = assertEquals(snapshot._2.hcursor.downField("sequence").get[Long]("value"),Right(7L))
          _ = assertEquals(snapshot._2.hcursor.downField("journal").focus.get.asArray.get.size,7)
          _ = assert(snapshot._2.noSpaces.contains("18:07"))
          _ = assert(!snapshot._2.noSpaces.contains("今晚九点见"))
          _ = assert(!snapshot._2.noSpaces.contains("霍斯"))
        yield (identity._3,snapshot._2,doneBody,done._2)
      }
      _ <- LocalHttpServer.resource(url,0).use { server => for
        restored <- send(server.getAddress.getPort,"getinvestigationsnapshot",payload(id),saved._1)
        retry <- send(server.getAddress.getPort,"comparetimes",saved._3,saved._1)
        _ = assertEquals(restored._2,saved._2)
        _ = assertEquals(retry._2,saved._4)
      yield () }
    yield ()).unsafeRunSync()
  }

  test("missing each material blocks comparison; wrong targets and unearned evidence cannot disclose replies") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      _ <- requiredPath.indices.toVector.traverse_ { skip =>
        val id = UUID.randomUUID().toString
        for
          _ <- send(port,"openstudy",payload(id),identity._3)
          end <- requiredPath.zipWithIndex.filterNot(_._2 == skip).foldLeft(IO.pure(0L)) { case (sequence,((point,route),index)) =>
            sequence.flatMap(seq => send(port,"investigate",inquiry(id,point,route,seq,s"without-$skip-$index"),identity._3)
              .map(reply => if reply._1 == 200 then seq+1 else seq))
          }
          result <- send(port,"comparetimes",comparison(id,seq=end),identity._3)
          wrongTarget <- send(port,"investigate",inquiry(id,"mary","clock_presented",end,"wrong-person"),identity._3)
          snapshot <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
          _ = assert(Set(403,409)(result._1))
          _ = assertEquals(wrongTarget._1,403)
          _ = assertEquals(snapshot._2.hcursor.downField("stage").get[Boolean]("completed"),Right(false))
        yield ()
      }
    yield () }
  }

  test("object registration is public but only earned object contents are projected") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      id = UUID.randomUUID().toString
      initial <- send(port,"openstudy",payload(id),identity._3)
      _ = assertEquals(initial._2.hcursor.downField("scene").get[Vector[Json]]("objects").toOption.get.size,9)
      _ = assert(initial._2.hcursor.get[Vector[Json]]("points").toOption.get.forall(_.hcursor.get[Vector[Json]]("findings").toOption.get.isEmpty))
      learned <- send(port,"investigate",inquiry(id,"paper","paper",0,"paper"),identity._3)
      snapshot <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
      _ = assertEquals(learned._1,200)
      paper = snapshot._2.hcursor.get[Vector[Json]]("points").toOption.get.find(_.hcursor.get[String]("id")==Right("point_paper")).get
      _ = assert(paper.hcursor.get[Vector[Json]]("findings").toOption.get.head.noSpaces.contains("18:20"))
      _ = assert(!snapshot._2.noSpaces.contains("不能保证"))
      _ = assert(!snapshot._2.noSpaces.contains("不等于死亡"))
    yield () }
  }

  test("window opening is authoritative, reversible and retryable without granting evidence") {
    withServer { port => for
      identity <- send(port,"openlocalidentity")
      id = UUID.randomUUID().toString
      _ <- send(port,"openstudy",payload(id),identity._3)
      bad <- send(port,"executeoperation",execute(id).replace("open_drawer","close_window"),identity._3)
      _ = assertEquals(bad._1,409)
      body = execute(id).replace("open_drawer","open_window")
      opened <- send(port,"executeoperation",body,identity._3)
      retry <- send(port,"executeoperation",body,identity._3)
      afterOpen <- send(port,"openstudy",payload(id),identity._3)
      _ = assertEquals(opened._1,200)
      _ = assertEquals(retry._2,opened._2)
      _ = assertEquals(afterOpen._2.hcursor.downField("scene").get[Boolean]("windowOpened"),Right(true))
      _ <- send(port,"executeoperation",execute(id,"close-window",1).replace("open_drawer","close_window"),identity._3)
      afterClose <- send(port,"getinvestigationsnapshot",payload(id),identity._3)
      _ = assertEquals(afterClose._2.hcursor.downField("scene").get[Boolean]("windowOpened"),Right(false))
      _ = assertEquals(afterClose._2.hcursor.get[Vector[Json]]("clues").toOption.get.size,0)
      _ = assertEquals(afterClose._2.hcursor.downField("sequence").get[Long]("value"),Right(2L))
    yield () }
  }
