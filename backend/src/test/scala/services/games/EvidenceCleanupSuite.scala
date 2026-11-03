package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import java.nio.file.Paths
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.games.api.*
import services.system.api.*
import services.system.objects.*

class EvidenceCleanupSuite extends FunSuite:
  private val owner=UserId("cleanup-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
    ImportCaseMessage(Paths.get("../docs/fixtures/investigation_base.json")).plan(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,point: String,route: String)=for
    before <- view(url,id)
    result <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,PointId(point),RouteId(route)).plan)
  yield result

  test("presenting an existing record saves the recipient and reply without granting another clue or fact") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url)
      _ <- tx(url)(services.TestCases.open(owner,id,CaseId("vicarage_study")))
      early <- read(url,id,"point_clement","route_clock_presented").attempt
      _ = assert(early.isLeft)
      _ <- read(url,id,"point_clock","route_clock")
      _ <- read(url,id,"point_clement","route_habit")
      before <- view(url,id)
      request=InvestigateMessage(owner,id,RequestId("present-clock"),before.sequence,PointId("point_clement"),RouteId("route_clock_presented"))
      first <- tx(url)(request.plan)
      retry <- tx(url)(request.plan)
      after <- view(url,id)
      _ = assertEquals(first,retry)
      _ = assert(first.acquiredClues.isEmpty)
      _ = assertEquals(after.clues,before.clues)
      clement=after.points.find(_.id == PointId("point_clement")).get
      _ = assertEquals(clement.presentations.size,1)
      _ = assertEquals(clement.presentations.head.evidence,Set(ClueId("clue_clock")))
      _ = assert(clement.presentations.head.reply.contains("拨快一刻钟"))
      _ = assert(!clement.routes.exists(_.id == RouteId("route_clock_presented")))
      _ = assert(after.points.filterNot(_.id == clement.id).forall(_.presentations.isEmpty))
      repeated <- read(url,id,"point_clement","route_clock_presented").attempt
      _ = assert(repeated.isLeft)
      restored <- tx(url)(services.TestCases.open(owner,id,CaseId("vicarage_study")))
      _ = assertEquals(restored,after)
    yield ()).unsafeRunSync()
  }
