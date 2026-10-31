package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import java.nio.file.{Files,Paths}
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.api.*
import services.system.api.*
import services.system.objects.*

class PoliceExchangeSuite extends FunSuite:
  private val owner=UserId("police-test-owner")
  private def database = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(run: java.sql.Connection => IO[A]): IO[A] =
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(run(c)))
  private def setup(url: String,id: SessionId): IO[Unit] = DatabaseSession.connection(url).use { c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *>
      OpenStudyMessage(owner,id).plan(c)).void
  }
  private def read(id: SessionId,point: String,route: String,seq: Long) =
    InvestigateMessage(owner,id,RequestId(s"read-$route"),Sequence(seq),PointId(point),RouteId(route))
  private def report(id: SessionId,seq: Long=1,request: String="report",evidence: Set[ClueId]=Set(ClueId("clue_clock"))) =
    ReportCluesMessage(owner,id,RequestId(request),Sequence(seq),evidence)
  private def view(url: String,id: SessionId) = tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def code(result: Either[Throwable,?]): Option[ErrorCode] = result.left.toOption.collect { case e: GameError => e.code }

  test("publishing police content does not break existing study/4 sessions or their original receipts") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c))
      _ <- tx(url) { c => for
        current <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        previous = current.copy(revision=Revision(3),content=LegacyFixture.withoutFollowUps(current.content).copy(police=None,witnesses=LegacyFixture.withoutFollowUps(current.content).witnesses.filterNot(w => current.content.police.contains(w.id)),publicPersons=Vector.empty,clock=None,externalChecks=Vector.empty,areaMap=None))
        _ <- CaseRevisionTable.insert(c,previous)
        _ <- StartGameMessage(owner,id,previous.caseId,previous.revision).plan(c)
      yield () }
      result <- tx(url)(read(id,"point_clock","route_clock",0).plan)
      saved <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(saved.caseRevision,Revision(3))
      _ = assertEquals(saved.sequence,Sequence(1))
      _ = assertEquals(saved.police,None)
      _ = assertEquals(saved.clues.map(_.id),Vector(ClueId("clue_clock")))
      _ = assertEquals(saved.scene.revision,"4")
      retry <- tx(url)(read(id,"point_clock","route_clock",0).plan)
      _ = assertEquals(retry,result)
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
      newId = SessionId(UUID.randomUUID().toString)
      current <- tx(url)(OpenStudyMessage(owner,newId).plan)
      _ = assertEquals(current.caseRevision,Revision(19))
      _ = assert(current.police.nonEmpty)
    yield ()).unsafeRunSync()
  }

  test("medical opinions are obtained from police, preserve approximate timing, and are not future inquest results") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assert(initial.stage.get.introduction.contains("遗体已送交医生检查"))
      _ = assertEquals(initial.clues.size,0)
      _ = assert(initial.points.flatMap(_.findings).isEmpty)
      _ = assertEquals(initial.police.get.exchanges.size,0)
      _ <- tx(url)(read(id,"point_police","route_police_medical",0).plan)
      saved <- view(url,id)
      _ = assertEquals(saved.clues.map(_.id).toSet,Set(ClueId("clue_medical_cause"),ClueId("clue_medical_time")))
      _ = assert(saved.clues.exists(_.text.contains("近距离枪击")))
      _ = assert(saved.clues.exists(_.text.contains("半小时左右")))
      _ = assert(!saved.clues.exists(_.text.contains("18:07")))
      _ = assertEquals(saved.sequence,Sequence(1))
      _ = assertEquals(saved.stage.get.completed,false)
      wrong <- tx(url)(read(id,"point_clement","route_police_medical",1).copy(requestId=RequestId("wrong-target")).plan).attempt
      _ = assertEquals(code(wrong),Some(ErrorCode.AccessDenied))
    yield ()).unsafeRunSync()
  }

  test("only acquired clues can be reported; retries, conflicts and ownership preserve one exchange") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      premature <- tx(url)(report(id,0).plan).attempt
      empty <- tx(url)(report(id,0,evidence=Set.empty).plan).attempt
      _ = assertEquals(code(premature),Some(ErrorCode.AccessDenied))
      _ = assertEquals(code(empty),Some(ErrorCode.InvalidValue))
      _ <- tx(url)(read(id,"point_clock","route_clock",0).plan)
      foreign <- tx(url)(report(id).copy(userId=UserId("someone-else")).plan).attempt
      _ = assertEquals(code(foreign),Some(ErrorCode.AccessDenied))
      result <- tx(url)(report(id).plan)
      retry <- tx(url)(report(id).plan)
      changed <- tx(url)(report(id,evidence=Set(ClueId("clue_future"))).plan).attempt
      repeated <- tx(url)(report(id,2,"another-report").plan).attempt
      saved <- view(url,id)
      _ = assertEquals(result,retry)
      _ = assertEquals(code(changed),Some(ErrorCode.RequestConflict))
      _ = assertEquals(code(repeated),Some(ErrorCode.ConditionFailed))
      _ = assertEquals(saved.sequence,Sequence(2))
      _ = assertEquals(saved.clues.size,1)
      _ = assertEquals(saved.journal.size,2)
      _ = assertEquals(saved.police.get.exchanges.size,1)
      _ = assertEquals(saved.police.get.sharedClues,Set(ClueId("clue_clock")))
      _ = assert(result.findings.head.value.contains("座钟读数"))
    yield ()).unsafeRunSync()
  }

  test("report ledger, journal, sequence and receipt all roll back together") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- tx(url)(read(id,"point_clock","route_clock",0).plan)
      before <- view(url,id)
      failed <- tx(url)(c => report(id).plan(c) *> IO.raiseError[Unit](new RuntimeException("force rollback"))).attempt
      _ = assert(failed.isLeft)
      after <- view(url,id)
      _ = assertEquals(after,before)
      _ <- tx(url)(report(id).plan)
      saved <- view(url,id)
      _ = assertEquals(saved.police.get.exchanges.size,1)
      _ = assertEquals(saved.sequence,Sequence(2))
    yield ()).unsafeRunSync()
  }

  test("concurrent reports commit once and file database reopening restores shared materials") {
    val directory=Files.createTempDirectory("police-exchange-")
    val url=s"jdbc:h2:file:${directory.resolve("case")};LOCK_TIMEOUT=5000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- tx(url)(read(id,"point_clock","route_clock",0).plan)
      replies <- Vector(1,2).parTraverse(_ => tx(url)(report(id).plan))
      _ = assertEquals(replies.head,replies.last)
      before <- view(url,id)
      _ <- setup(url,id)
      restored <- view(url,id)
      _ = assertEquals(restored,before)
      _ = assertEquals(restored.police.get.exchanges.size,1)
      stale <- tx(url)(report(id,0,"stale").plan).attempt
      _ = assertEquals(code(stale),Some(ErrorCode.StaleState))
    yield ()).unsafeRunSync()
  }
