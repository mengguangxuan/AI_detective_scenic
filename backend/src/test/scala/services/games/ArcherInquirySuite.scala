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

class ArcherInquirySuite extends FunSuite:
  private val owner=UserId("archer-owner")
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

  private def directions(url: String,id: SessionId): IO[Unit]=for
    _ <- prepare(url,id)
    _ <- reviseRedding(url,id)
    _ <- read(url,id,"redding","route_redding_gun_revised")
    _ <- read(url,id,"anne","route_anne_gun_challenge")
    _ <- read(url,id,"anne","route_anne_redding_challenge")
    _ <- execute(url,id,"begin_anne_followup")
    _ <- read(url,id,"anne","route_anne_revised")
    _ <- read(url,id,"police","route_police_revised_review")
  yield ()

  test("Archer contact needs the actual investigation direction and gun statement; guessing never calls the model") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val forbidden=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.raiseError(new AssertionError("hidden witness reached model"))
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assertEquals(initial.caseRevision,Revision(18))
      _ = assert(initial.people.find(_.id == PersonId("mrs_archer")).get.witness.isEmpty)
      contact <- execute(url,id,"open_archer_meeting").attempt
      fixed <- read(url,id,"archer","route_archer_gun_memory").attempt
      free <- ask(url,id,"archer",forbidden).attempt
      _ = assertEquals(code(contact),Some(ErrorCode.ConditionFailed))
      _ = assertEquals(code(fixed),Some(ErrorCode.AccessDenied))
      _ = assertEquals(code(free),Some(ErrorCode.AccessDenied))
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,initial)
    yield ()).unsafeRunSync()
  }

  test("explicit contact grants no testimony; model receives only Archer's own knowledge and actual presentations") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(input.user.contains("阿彻太太"))
        assert(input.user.contains("十二点三刻"))
        val data=io.circe.parser.parse(input.user).toOption.get.hcursor
        assertEquals(data.get[Vector[io.circe.Json]]("shownEvidence"),Right(Vector.empty))
        assertEquals(data.get[Vector[String]]("privateContext"),Right(Vector.empty))
        assert(!input.user.contains("共谋"))
        response("archer_gun_memory")
      }
    (for
      _ <- directions(url,id)
      before <- view(url,id)
      _ <- execute(url,id,"open_archer_meeting")
      contacted <- view(url,id)
      _ = assertEquals(contacted.clues,before.clues)
      _ = assertEquals(contacted.clock.get.elapsedSeconds,before.clock.get.elapsedSeconds+120L)
      _ = assert(contacted.witnesses.exists(_.id == WitnessId("archer")))
      _ <- ask(url,id,"archer",client("police_archer_review"))
      rejected <- view(url,id)
      _ = assertEquals(rejected.interviews.last.mode,InterviewReplyMode.Rejected)
      _ <- ask(url,id,"archer",model)
      after <- view(url,id)
      _ = assertEquals(after.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assert(after.clues.exists(_.id == ClueId("clue_archer_gun_memory")))
      _ = assert(!after.clues.exists(_.id == ClueId("clue_archer_household")))
      _ = assert(!after.police.get.sharedClues(ClueId("clue_archer_gun_memory")))
      _ = assert(!after.asJson.noSpaces.contains("privateContext"))
    yield ()).unsafeRunSync()
  }

  test("police requires acquired and actually presented statements; fixed inquiry, retry and reopen preserve both versions") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("archer-inquiry-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    val materials=Set("clue_redding_gun_revised","clue_archer_gun_memory","clue_archer_household").map(ClueId.apply)
    (for
      _ <- directions(url,id)
      before <- view(url,id)
      contact=ExecuteOperationMessage(owner,id,RequestId("contact-archer"),before.sequence,OperationId("open_archer_meeting"))
      accepted <- tx(url)(contact.plan)
      duplicate <- tx(url)(contact.plan)
      _ = assertEquals(accepted,duplicate)
      forged <- ask(url,id,"melchett",client("police_archer_review"),materials).attempt
      _ = assertEquals(code(forged),Some(ErrorCode.AccessDenied))
      _ <- read(url,id,"archer","route_archer_gun_memory")
      partial <- read(url,id,"police","route_police_archer_review").attempt
      _ = assertEquals(code(partial),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"archer","route_archer_household")
      _ <- ask(url,id,"melchett",client("police_archer_review"))
      unshared <- view(url,id)
      _ = assertEquals(unshared.interviews.last.mode,InterviewReplyMode.Rejected)
      _ <- read(url,id,"police","route_police_archer_review")
      _ <- ask(url,id,"melchett",client("police_archer_review"))
      saved <- view(url,id)
      _ = assertEquals(saved.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assert(materials.subsetOf(saved.police.get.sharedClues))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_police_archer_review")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_gun")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_gun_revised")))
      _ = assert(!saved.stage.get.completed)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      retried <- tx(url)(contact.plan)
      _ = assertEquals(restored,saved)
      _ = assertEquals(retried,accepted)
    yield ()).unsafeRunSync()
  }

  test("revision 10 keeps its original cast and receipt after revision 11 is published") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(10),content=LegacyFixture.withoutArcher(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield () }
      _ <- read(url,id,"redding","route_redding_account")
      saved <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(saved.caseRevision,Revision(10))
      _ = assert(!saved.people.exists(_.id == PersonId("mrs_archer")))
      _ = assert(!saved.points.flatMap(_.operations).exists(_.id == OperationId("open_archer_meeting")))
      denied <- execute(url,id,"open_archer_meeting").attempt
      _ = assertEquals(code(denied),Some(ErrorCode.AccessDenied))
      receipt <- tx(url)(InvestigateMessage(owner,id,RequestId("route_redding_account"),Sequence(0),PointId("point_redding"),RouteId("route_redding_account")).plan)
      _ = assertEquals(receipt.sequence,Sequence(1))
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,saved)
    yield ()).unsafeRunSync()
  }
