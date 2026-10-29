package services.games
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import java.util.UUID
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

class FollowUpSuite extends FunSuite:
  private val owner=UserId("followup-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(run: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(run(c)))
  private def setup(url: String,id: SessionId)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)).void)
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,who: String,route: String): IO[Unit]=for
    before <- view(url,id)
    _ <- tx(url)(InvestigateMessage(owner,id,RequestId(route),before.sequence,PointId("point_"+who),RouteId(route)).plan)
  yield ()
  private def execute(url: String,id: SessionId,op: String): IO[Unit]=for
    before <- view(url,id)
    _ <- tx(url)(ExecuteOperationMessage(owner,id,RequestId(op),before.sequence,OperationId(op)).plan)
  yield ()
  private def response(answer: String)=s"""{"reply":"这是我现在的补充说法。","disclosureIds":["$answer"]}"""
  private def client(answer: String)=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO.pure(response(answer))
  private def ask(url: String,id: SessionId,who: String,model: ChatCompletionClient,evidence: Set[ClueId]=Set.empty): IO[Unit]=for
    before <- view(url,id)
    _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,WitnessId(who),"请说明你现在的说法。",evidence).run(url,model)
  yield ()
  private def code(result: Either[Throwable,?])=result.left.toOption.collect { case e: GameError => e.code }
  private def prepare(url: String,id: SessionId): IO[Unit]=for
    _ <- setup(url,id)
    _ <- read(url,id,"redding","route_redding_account")
    _ <- read(url,id,"redding","route_redding_gun")
    _ <- read(url,id,"clock","route_clock")
    _ <- read(url,id,"redding","route_redding_clock")
    _ <- read(url,id,"police","route_police_medical")
    a <- view(url,id)
    _ <- tx(url)(RequestExternalCheckMessage(owner,id,RequestId("doctor"),a.sequence,ExternalCheckId("medical_timing")).plan)
    b <- view(url,id)
    _ <- tx(url)(RequestExternalCheckMessage(owner,id,RequestId("gun"),b.sequence,ExternalCheckId("gun_verification")).plan)
    _ <- execute(url,id,"open_anne_meeting")
    _ <- read(url,id,"anne","route_anne_statement")
    _ <- read(url,id,"anne","route_anne_time")
    _ <- read(url,id,"anne","route_anne_gun")
    _ <- execute(url,id,"open_valet_meeting")
    _ <- read(url,id,"valet","route_valet_gun")
    _ <- execute(url,id,"open_movement_meetings")
    _ <- read(url,id,"stone","route_stone_inn")
    _ <- read(url,id,"marple","route_marple_visitors")
    _ <- read(url,id,"hartnell","route_hartnell_visit")
  yield ()
  private def challengeRedding(url: String,id: SessionId)=
    read(url,id,"redding","route_redding_time_challenge") *> read(url,id,"redding","route_redding_anne_reply")
  private def reviseRedding(url: String,id: SessionId)=
    challengeRedding(url,id) *> execute(url,id,"begin_redding_followup") *> read(url,id,"redding","route_redding_revised")

  test("owning evidence does not unlock a follow-up or reveal future model answers") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(!input.user.contains("\"id\":\"redding_revised\""))
        assert(!input.user.contains("枪在地上"))
        response("redding_revised")
      }
    (for
      _ <- prepare(url,id)
      before <- view(url,id)
      _ = assert(before.clues.exists(_.id == ClueId("clue_gun_verification")))
      _ = assert(!before.asJson.noSpaces.contains("clue_redding_revised"))
      early <- execute(url,id,"begin_redding_followup").attempt
      route <- read(url,id,"redding","route_redding_revised").attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ = assertEquals(code(route),Some(ErrorCode.ConditionFailed))
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,before)
      _ <- ask(url,id,"redding",model)
      after <- view(url,id)
      _ = assertEquals(after.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assert(!after.clues.exists(_.id == ClueId("clue_redding_revised")))
      _ = assert(!after.followUps.find(_.witness == WitnessId("redding")).get.ready)
    yield ()).unsafeRunSync()
  }

  test("presentations to another person, unavailable replies and unanswered challenges cannot satisfy preparation") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val shown=Set("clue_anne_statement","clue_stone_inn","clue_medical_time","clue_medical_visit_record","clue_gun_verification").map(ClueId.apply)
    val unavailable=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.raiseError(new RuntimeException("offline"))
    val ordinary=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.pure("""{"reply":"我看过这些材料了。","disclosureIds":[]}""")
    (for
      _ <- prepare(url,id)
      _ <- ask(url,id,"clement",ordinary,shown)
      _ <- ask(url,id,"redding",unavailable,shown)
      failed <- view(url,id)
      _ = assert(failed.followUps.find(_.witness == WitnessId("redding")).get.requirements.forall(!_.met))
      _ <- ask(url,id,"redding",ordinary,shown)
      shownOnly <- view(url,id)
      requirements=shownOnly.followUps.find(_.witness == WitnessId("redding")).get.requirements
      _ = assertEquals(requirements.count(_.met),5)
      early <- execute(url,id,"begin_redding_followup").attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ <- challengeRedding(url,id)
      ready <- view(url,id)
      _ = assert(ready.followUps.find(_.witness == WitnessId("redding")).get.ready)
    yield ()).unsafeRunSync()
  }

  test("explicit beginning is atomic and retryable, and does not automatically create a revised statement") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- prepare(url,id)
      _ <- challengeRedding(url,id)
      before <- view(url,id)
      command=ExecuteOperationMessage(owner,id,RequestId("begin"),before.sequence,OperationId("begin_redding_followup"))
      failed <- tx(url)(c => command.plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(failed.isLeft)
      rolledBack <- view(url,id)
      _ = assertEquals(rolledBack,before)
      result <- tx(url)(command.plan)
      retry <- tx(url)(command.plan)
      after <- view(url,id)
      _ = assertEquals(result,retry)
      _ = assertEquals(after.clock.get.elapsedSeconds,before.clock.get.elapsedSeconds+120)
      _ = assert(after.followUps.find(_.witness == WitnessId("redding")).get.started)
      _ = assert(!after.clues.exists(_.id == ClueId("clue_redding_revised")))
      again <- execute(url,id,"begin_redding_followup").attempt
      _ = assertEquals(code(again),Some(ErrorCode.ConditionFailed))
    yield ()).unsafeRunSync()
  }

  test("model switches its outward policy, retains private truth and preserves first statements and original receipts") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(input.user.contains("本轮补充供述对外说话"))
        assert(input.user.contains("你知道自己与安妮有私情并参与这宗命案的共谋"))
        assert(input.user.contains("\"id\":\"redding_revised\""))
        assert(!input.user.contains("\"id\":\"redding_account\""))
        response("redding_revised")
      }
    (for
      _ <- prepare(url,id)
      before <- view(url,id)
      original=before.clues.find(_.id == ClueId("clue_redding_account")).get
      _ <- challengeRedding(url,id)
      _ <- execute(url,id,"begin_redding_followup")
      _ <- ask(url,id,"redding",model)
      _ <- read(url,id,"redding","route_redding_gun_revised")
      _ <- read(url,id,"redding","route_redding_scene_revised")
      after <- view(url,id)
      _ = assertEquals(after.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assertEquals(after.clues.find(_.id == original.id).get,original)
      comparison=after.statementComparisons.find(_.witness == WitnessId("redding")).get
      _ = assert(comparison.rows.forall(row => row.initial.nonEmpty && row.revised.nonEmpty))
      _ = assertEquals(comparison.rows.head.initial.get.sequence,Sequence(1))
      _ = assert(comparison.rows.head.revised.get.sequence.value > comparison.rows.head.initial.get.sequence.value)
      old <- tx(url)(InvestigateMessage(owner,id,RequestId("route_redding_account"),Sequence(0),PointId("point_redding"),RouteId("route_redding_account")).plan)
      restored <- view(url,id)
      _ = assertEquals(old.sequence,Sequence(1))
      _ = assertEquals(restored,after)
      _ = assert(!after.asJson.noSpaces.contains("privateContext"))
      _ = assertEquals(after.stage.get.completed,false)
    yield ()).unsafeRunSync()
  }

  test("Anne needs the actual revised Redding statement; both versions and police presentation survive reopening") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("revised-statements-").resolve("case")};LOCK_TIMEOUT=3000";val id=SessionId(UUID.randomUUID().toString)
    val reports=Set("clue_redding_account","clue_anne_statement","clue_redding_revised","clue_anne_revised").map(ClueId.apply)
    (for
      _ <- prepare(url,id)
      _ <- read(url,id,"anne","route_anne_gun_challenge")
      premature <- read(url,id,"anne","route_anne_redding_challenge").attempt
      _ = assertEquals(code(premature),Some(ErrorCode.ConditionFailed))
      _ <- reviseRedding(url,id)
      before <- view(url,id)
      _ = assert(!before.followUps.find(_.witness == WitnessId("anne")).get.ready)
      _ <- read(url,id,"anne","route_anne_redding_challenge")
      _ <- execute(url,id,"begin_anne_followup")
      _ <- ask(url,id,"anne",client("anne_revised"))
      _ <- read(url,id,"anne","route_anne_visit_revised")
      _ <- read(url,id,"anne","route_anne_gun_revised")
      _ <- ask(url,id,"melchett",client("police_revised_review"))
      notShared <- view(url,id)
      _ = assertEquals(notShared.interviews.last.mode,InterviewReplyMode.Rejected)
      _ <- ask(url,id,"melchett",client("police_revised_review"),reports)
      saved <- view(url,id)
      _ = assertEquals(saved.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_police_revised_review")))
      _ = assert(reports.subsetOf(saved.police.get.sharedClues))
      _ = assert(saved.statementComparisons.find(_.witness == WitnessId("anne")).get.rows.forall(_.revised.nonEmpty))
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
    yield ()).unsafeRunSync()
  }

  test("revision 8 keeps its original content and time rules when revision 9 is available") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(8),content=LegacyFixture.withoutFollowUps(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield () }
      _ <- read(url,id,"redding","route_redding_account")
      saved <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(saved.caseRevision,Revision(8))
      _ = assertEquals(saved.followUps,Vector.empty)
      _ = assertEquals(saved.statementComparisons,Vector.empty)
      _ = assertEquals(saved.clock.get.elapsedSeconds,120L)
      before <- view(url,id)
      _ = assertEquals(before,saved)
    yield ()).unsafeRunSync()
  }
