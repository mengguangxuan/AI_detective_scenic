package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import java.util.UUID
import scala.util.Using
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.api.*
import services.games.objects.*
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class ExternalChecksSuite extends FunSuite:
  private val owner=UserId("clock-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(run: java.sql.Connection => IO[A]): IO[A] = DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(run(c)))
  private def setup(url: String,id: SessionId): IO[Unit]=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
    ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)).void)
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def request(id: SessionId,check: String="gun_verification",seq: Long=0)=RequestExternalCheckMessage(owner,id,RequestId(check),Sequence(seq),ExternalCheckId(check))
  private def waitFor(id: SessionId,seq: Long,minutes: Int=15)=AdvanceTimeMessage(owner,id,RequestId(s"wait-$seq"),Sequence(seq),minutes)
  private def code(result: Either[Throwable,?])=result.left.toOption.collect { case e: GameError => e.code }

  test("public map and clock do not reveal unchecked results; view reads do not advance time") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      a <- view(url,id)
      b <- view(url,id)
      _ = assertEquals(a,b)
      _ = assertEquals(a.clock.get.label,"周五 09:00")
      _ = assertEquals(a.clock.get.elapsedSeconds,0L)
      _ = assert(a.areaMap.get.locations.exists(_.id == PlaceId("haydock_home")))
      _ = assert(a.areaMap.get.roads.exists(r => r.from == PlaceId("study") && r.to == PlaceId("lower_farm") && r.meters == 3100))
      _ = assert(!a.asJson.noSpaces.contains(".25"))
      _ = assert(!a.asJson.noSpaces.contains("clue_medical_visit_record"))
      _ = assert(a.externalChecks.forall(c => c.available && c.progress.isEmpty))
      early <- tx(url)(InvestigateMessage(owner,id,RequestId("bypass"),Sequence(0),PointId("point_police"),RouteId("route_gun_verification")).plan).attempt
      _ = assertEquals(code(early),Some(ErrorCode.AccessDenied))
    yield ()).unsafeRunSync()
  }

  test("requesting and exact retries do not duplicate checks or time; deadlines produce persisted results") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("timed-check-").resolve("case")};LOCK_TIMEOUT=3000";val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      started <- tx(url)(request(id).plan)
      retry <- tx(url)(request(id).plan)
      _ = assertEquals(started,retry)
      pending <- view(url,id)
      _ = assertEquals(pending.clock.get.elapsedSeconds,120L)
      _ = assertEquals(pending.externalChecks.find(_.id == ExternalCheckId("gun_verification")).get.progress.get.readyAt,"周五 09:20")
      _ <- tx(url)(waitFor(id,1).plan)
      before <- view(url,id)
      _ = assertEquals(before.clock.get.elapsedSeconds,1020L)
      _ = assert(!before.clues.exists(_.id == ClueId("clue_gun_verification")))
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restoredPending <- view(url,id)
      _ = assertEquals(restoredPending,before)
      completed <- tx(url)(waitFor(id,2,3).plan)
      repeated <- tx(url)(waitFor(id,2,3).plan)
      _ = assertEquals(completed,repeated)
      done <- view(url,id)
      _ = assertEquals(done.clock.get.elapsedSeconds,1200L)
      _ = assertEquals(done.externalChecks.find(_.id == ExternalCheckId("gun_verification")).get.progress.get.completedAt,Some("周五 09:20"))
      _ = assert(done.clues.find(_.id == ClueId("clue_gun_verification")).get.text.contains(".25"))
      _ = assert(completed.acquiredClues(ClueId("clue_gun_verification")))
      _ = assertEquals(done.journal.count(_.clueIds(ClueId("clue_gun_verification"))),1)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restoredDone <- view(url,id)
      _ = assertEquals(restoredDone,done)
    yield ()).unsafeRunSync()
  }

  test("ordinary investigation advances time and completes the doctor check without an explicit wait") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- tx(url)(request(id,"medical_timing").plan)
      _ <- tx(url)(InvestigateMessage(owner,id,RequestId("clock"),Sequence(1),PointId("point_clock"),RouteId("route_clock")).plan)
      _ <- tx(url)(waitFor(id,2,5).plan)
      pending <- view(url,id)
      _ = assertEquals(pending.clock.get.elapsedSeconds,540L)
      _ = assert(!pending.clues.exists(_.id == ClueId("clue_medical_visit_record")))
      result <- tx(url)(InvestigateMessage(owner,id,RequestId("mary"),Sequence(3),PointId("point_mary"),RouteId("route_arrival")).plan)
      done <- view(url,id)
      _ = assertEquals(done.clock.get.elapsedSeconds,660L)
      _ = assert(result.acquiredClues(ClueId("clue_medical_visit_record")))
      _ = assert(done.clues.find(_.id == ClueId("clue_medical_visit_record")).get.text.contains("约19:00"))
      _ = assert(done.points.find(_.id == PointId("point_police")).get.findings.exists(_.id == ClueId("clue_medical_visit_record")))
    yield ()).unsafeRunSync()
  }

  test("bad input, foreign ownership and duplicate applications have no time or state effects; waits are atomic") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      foreign <- tx(url)(request(id).copy(userId=UserId("foreign")).plan).attempt
      invalid <- tx(url)(waitFor(id,0,0).plan).attempt
      _ = assertEquals(code(foreign),Some(ErrorCode.AccessDenied))
      _ = assertEquals(code(invalid),Some(ErrorCode.InvalidValue))
      _ <- tx(url)(request(id).plan)
      before <- view(url,id)
      duplicate <- tx(url)(request(id,seq=1).copy(requestId=RequestId("again")).plan).attempt
      _ = assertEquals(code(duplicate),Some(ErrorCode.ConditionFailed))
      failed <- tx(url)(c => waitFor(id,1,20).plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(failed.isLeft)
      rolledBack <- view(url,id)
      _ = assertEquals(rolledBack,before)
      replies <- Vector.fill(2)(tx(url)(waitFor(id,1,20).plan)).parSequence
      _ = assertEquals(replies(0),replies(1))
      done <- view(url,id)
      _ = assertEquals(done.clock.get.elapsedSeconds,1320L)
      _ = assertEquals(done.sequence,Sequence(2))
      _ = assertEquals(done.journal.size,2)
    yield ()).unsafeRunSync()
  }

  test("police learn actual completed results, other witnesses do not automatically receive them") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    def question(seq: Long,witness: String,client: ChatCompletionClient)=AskWitnessMessage(owner,id,RequestId(s"ask-$seq"),Sequence(seq),WitnessId(witness),"枪支核验怎么样了？",Set.empty).run(url,client)
    def client(check: ChatCompletionInput => Unit)=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO { check(input); """{"reply":"我会按现有记录说明情况。","disclosureIds":[]}""" }
    (for
      _ <- setup(url,id)
      _ <- tx(url)(request(id).plan)
      _ <- question(1,"melchett",client(input => { assert(input.user.contains("预计周五 09:20"));assert(!input.user.contains("送交登记中的手枪"));assert(!input.user.contains(".25"));assert(!input.user.contains("约19:00"));assert(!input.user.contains("\"id\":\"gun_verification\"")) }))
      _ <- tx(url)(waitFor(id,2,20).plan)
      _ <- question(3,"melchett",client(input => { assert(input.user.contains("送交登记中的手枪"));assert(input.user.contains("已完成"));assert(input.user.contains("\"id\":\"gun_verification\"")) }))
      _ <- question(4,"mary",client(input => assert(!input.user.contains("送交登记中的手枪"))))
    yield ()).unsafeRunSync()
  }

  test("existing revision 7 and a database without the new column restore without time migration") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(7),content=LegacyFixture.withoutFollowUps(latest.content).copy(clock=None,areaMap=None,externalChecks=Vector.empty))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield () }
      receipt <- tx(url)(InvestigateMessage(owner,id,RequestId("old-clock"),Sequence(0),PointId("point_clock"),RouteId("route_clock")).plan)
      before <- view(url,id)
      _ <- DatabaseSession.connection(url).use { c => IO.blocking { Using.resource(c.createStatement())(_.execute("ALTER TABLE game_sessions DROP COLUMN checks_json")); () } *> DatabaseSchema.initialize(c) }
      restored <- view(url,id)
      retry <- tx(url)(InvestigateMessage(owner,id,RequestId("old-clock"),Sequence(0),PointId("point_clock"),RouteId("route_clock")).plan)
      denied <- tx(url)(waitFor(id,1).plan).attempt
      _ = assertEquals(restored,before)
      _ = assertEquals(receipt,retry)
      _ = assertEquals(restored.clock,None)
      _ = assertEquals(restored.externalChecks,Vector.empty)
      _ = assertEquals(code(denied),Some(ErrorCode.ConflictingContent))
    yield ()).unsafeRunSync()
  }

  test("completed reports have legal disclosure references without executing routes or leaking to other people") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val reportClient=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.pure(
        """{"reply":"这份记录写的是约七点前后到场，随后立即初查。","disclosureIds":["medical_visit_record"]}""")
    def ask(seq: Long,witness: String,evidence: Set[ClueId]=Set.empty)=
      AskWitnessMessage(owner,id,RequestId(s"report-$seq"),Sequence(seq),WitnessId(witness),"医生到场记录怎样说？",evidence).run(url,reportClient)
    (for
      _ <- setup(url,id)
      _ <- tx(url)(request(id,"medical_timing").plan)
      _ <- ask(1,"melchett")
      early <- view(url,id)
      _ = assertEquals(early.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assert(early.clues.isEmpty)
      _ <- tx(url)(waitFor(id,2,15).plan)
      result <- ask(3,"melchett")
      _ = assertEquals(result.acquiredClues,Set.empty[ClueId])
      _ <- ask(4,"mary")
      _ <- ask(5,"mary",Set(ClueId("clue_medical_visit_record")))
      done <- view(url,id)
      _ = assertEquals(done.interviews.map(_.mode),Vector(InterviewReplyMode.Rejected,InterviewReplyMode.Answered,InterviewReplyMode.Rejected,InterviewReplyMode.Answered))
      _ = assertEquals(done.clues.map(_.id),Vector(ClueId("clue_medical_visit_record")))
    yield ()).unsafeRunSync()
  }
