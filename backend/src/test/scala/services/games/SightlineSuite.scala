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

class SightlineSuite extends FunSuite:
  private val owner=UserId("sightline-owner")
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

  private def sources(url: String,id: SessionId): IO[Unit]=for
    _ <- prepare(url,id)
    _ <- reviseRedding(url,id)
    _ <- read(url,id,"anne","route_anne_gun_challenge")
    _ <- read(url,id,"anne","route_anne_redding_challenge")
    _ <- execute(url,id,"begin_anne_followup")
    _ <- read(url,id,"anne","route_anne_visit_revised")
  yield ()
  private def request(url: String,id: SessionId): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(RequestExternalCheckMessage(owner,id,RequestId("sightline-request"),s.sequence,ExternalCheckId("sightline_recheck")).plan)
  yield ()
  private def waitFor(url: String,id: SessionId,minutes: Int): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(AdvanceTimeMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,minutes).plan)
  yield ()
  private def complete(url: String,id: SessionId): IO[Unit]=
    sources(url,id) *> read(url,id,"police","route_sightline_basis") *> request(url,id) *> waitFor(url,id,10)

  test("owning statements alone does not submit the observation basis or disclose future reenactment") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val pending=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(!input.user.contains("一名警员坐在书桌前"))
        response("sightline_recheck")
      }
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assertEquals(initial.caseRevision,Revision(19))
      _ = assert(!initial.externalChecks.find(_.id == ExternalCheckId("sightline_recheck")).get.available)
      early <- request(url,id).attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ <- sources(url,id)
      owned <- view(url,id)
      _ = assert(owned.clues.exists(_.id == ClueId("clue_anne_visit_revised")))
      _ = assert(!owned.police.get.sharedClues(ClueId("clue_anne_visit_revised")))
      unregistered <- request(url,id).attempt
      _ = assertEquals(code(unregistered),Some(ErrorCode.ConditionFailed))
      _ <- ask(url,id,"melchett",client("sightline_basis"))
      notPresented <- view(url,id)
      _ = assertEquals(notPresented.interviews.last.mode,InterviewReplyMode.Rejected)
      _ <- read(url,id,"police","route_sightline_basis")
      basis <- view(url,id)
      _ = assert(Set("clue_anne_visit_revised","clue_marple_visitors").map(ClueId.apply).subsetOf(basis.police.get.sharedClues))
      _ = assert(!basis.clues.exists(_.id == ClueId("clue_sightline_recheck")))
      _ <- request(url,id)
      _ <- ask(url,id,"melchett",pending)
      rejected <- view(url,id)
      _ = assertEquals(rejected.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assert(!rejected.clues.exists(_.id == ClueId("clue_sightline_recheck")))
    yield ()).unsafeRunSync()
  }

  test("request rollback, exact retries, pending reopening and a bounded result preserve both original accounts") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("sightline-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- sources(url,id)
      _ <- read(url,id,"police","route_sightline_basis")
      before <- view(url,id)
      originals=before.clues.filter(c => Set("clue_anne_visit_revised","clue_marple_visitors")(c.id.value))
      command=RequestExternalCheckMessage(owner,id,RequestId("sightline"),before.sequence,ExternalCheckId("sightline_recheck"))
      failed <- tx(url)(c => command.plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(failed.isLeft)
      rolledBack <- view(url,id)
      _ = assertEquals(rolledBack,before)
      accepted <- tx(url)(command.plan)
      repeated <- tx(url)(command.plan)
      _ = assertEquals(accepted,repeated)
      _ <- waitFor(url,id,5)
      pending <- view(url,id)
      _ = assert(!pending.clues.exists(_.id == ClueId("clue_sightline_recheck")))
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      reopened <- view(url,id)
      _ = assertEquals(reopened,pending)
      _ <- waitFor(url,id,5)
      done <- view(url,id)
      _ = assert(done.clues.find(_.id == ClueId("clue_sightline_recheck")).get.text.contains("未遍查其他窗外站位"))
      _ = assertEquals(done.clues.filter(c => originals.map(_.id).contains(c.id)),originals)
      _ = assertEquals(done.journal.count(_.clueIds(ClueId("clue_sightline_recheck"))),1)
      _ = assert(!done.stage.get.completed)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      retried <- tx(url)(command.plan)
      _ = assertEquals(restored,done)
      _ = assertEquals(retried,accepted)
    yield ()).unsafeRunSync()
  }

  test("completed results are police knowledge; Anne and Marple respond only after actual presentation") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val report=Set(ClueId("clue_sightline_recheck"))
    val scoped=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(input.user.contains("一名警员坐在书桌前"))
        assert(!input.user.contains("你本人知道的共谋"))
        response("marple_sightline_reply")
      }
    (for
      _ <- complete(url,id)
      _ <- ask(url,id,"melchett",client("sightline_recheck"))
      _ <- ask(url,id,"anne",client("anne_sightline_reply"))
      _ <- ask(url,id,"marple",client("marple_sightline_reply"))
      unshared <- view(url,id)
      _ = assertEquals(unshared.interviews.takeRight(3).map(_.mode),Vector(InterviewReplyMode.Answered,InterviewReplyMode.Rejected,InterviewReplyMode.Rejected))
      _ <- ask(url,id,"marple",scoped,report)
      _ <- ask(url,id,"anne",client("anne_sightline_reply"))
      isolated <- view(url,id)
      _ = assertEquals(isolated.interviews.last.mode,InterviewReplyMode.Rejected)
      _ <- read(url,id,"anne","route_anne_sightline_reply")
      _ <- read(url,id,"police","route_police_sightline_review")
      saved <- view(url,id)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_anne_sightline_reply")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_marple_sightline_reply")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_police_sightline_review")))
      _ = assert(saved.police.get.sharedClues(ClueId("clue_sightline_recheck")))
      _ = assert(!saved.asJson.noSpaces.contains("privateContext"))
    yield ()).unsafeRunSync()
  }

  test("revision 12 keeps paper checks and original receipts without receiving a sightline feature") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(12),content=LegacyFixture.withoutSightline(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield () }
      _ <- read(url,id,"clock","route_clock")
      saved <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(saved.caseRevision,Revision(12))
      _ = assertEquals(saved.externalChecks.size,4)
      denied <- request(url,id).attempt
      _ = assertEquals(code(denied),Some(ErrorCode.AccessDenied))
      retry <- tx(url)(InvestigateMessage(owner,id,RequestId("route_clock"),Sequence(0),PointId("point_clock"),RouteId("route_clock")).plan)
      _ = assertEquals(retry.sequence,Sequence(1))
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
    yield ()).unsafeRunSync()
  }
