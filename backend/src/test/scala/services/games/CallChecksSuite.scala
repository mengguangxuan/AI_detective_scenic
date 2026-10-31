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

class CallChecksSuite extends FunSuite:
  private val owner=UserId("call-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId): IO[Unit]=DatabaseSession.connection(url).use(c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)).void)
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,point: String,route: String): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,PointId(point),RouteId(route)).plan)
  yield ()
  private def request(url: String,id: SessionId,check: String): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(RequestExternalCheckMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,ExternalCheckId(check)).plan)
  yield ()
  private def waitFor(url: String,id: SessionId,minutes: Int): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(AdvanceTimeMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,minutes).plan)
  yield ()
  private def ask(url: String,id: SessionId,who: String,model: ChatCompletionClient,evidence: Set[ClueId]=Set.empty): IO[Unit]=for
    s <- view(url,id)
    _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,WitnessId(who),"牧师来电核查有什么结果？",evidence).run(url,model)
  yield ()
  private def response(answer: String)=s"""{"reply":"我按目前记录说明。","disclosureIds":["$answer"]}"""
  private def client(answer: String)=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO.pure(s"""{"reply":"我按目前记录说明。","disclosureIds":["$answer"]}""")
  private def code(value: Either[Throwable,?])=value.left.toOption.collect { case e: GameError => e.code }
  private def basis(url: String,id: SessionId): IO[Unit]=
    read(url,id,"point_clement","route_call") *> read(url,id,"point_police","route_call_basis")
  private def trace(url: String,id: SessionId): IO[Unit]=
    basis(url,id) *> request(url,id,"call_origin") *> waitFor(url,id,15)
  private def completed(url: String,id: SessionId): IO[Unit]=
    trace(url,id) *> request(url,id,"call_lodge_inspection") *> waitFor(url,id,10)

  test("a personal account must actually reach police before tracing; a location and inspection cannot be guessed") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val pending=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(!input.user.contains("北门房"))
        response("call_origin")
      }
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assertEquals(initial.caseRevision,Revision(21))
      _ = assert(initial.externalChecks.filter(_.id.value.startsWith("call_")).forall(!_.available))
      early <- request(url,id,"call_origin").attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"point_clement","route_call")
      owned <- request(url,id,"call_origin").attempt
      _ = assertEquals(code(owned),Some(ErrorCode.ConditionFailed))
      _ <- ask(url,id,"melchett",client("call_basis"))
      unshared <- view(url,id)
      _ = assertEquals(unshared.interviews.last.mode,InterviewReplyMode.Rejected)
      _ <- read(url,id,"point_police","route_call_basis")
      recorded <- view(url,id)
      _ = assert(recorded.police.get.sharedClues(ClueId("clue_call")))
      _ <- request(url,id,"call_origin")
      _ <- ask(url,id,"melchett",pending)
      rejected <- view(url,id)
      _ = assertEquals(rejected.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assert(!rejected.clues.exists(_.id == ClueId("clue_call_origin")))
      premature <- request(url,id,"call_lodge_inspection").attempt
      _ = assertEquals(code(premature),Some(ErrorCode.ConditionFailed))
    yield ()).unsafeRunSync()
  }

  test("tracing and site inspection complete separately; original statement, rollback, retry and pending reopen are preserved") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("call-checks-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- basis(url,id)
      first <- view(url,id)
      original=first.clues.find(_.id == ClueId("clue_call")).get
      command=RequestExternalCheckMessage(owner,id,RequestId("trace-call"),first.sequence,ExternalCheckId("call_origin"))
      failed <- tx(url)(c => command.plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(failed.isLeft)
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,first)
      accepted <- tx(url)(command.plan)
      repeated <- tx(url)(command.plan)
      _ = assertEquals(accepted,repeated)
      _ <- waitFor(url,id,5)
      pending <- view(url,id)
      _ = assert(!pending.clues.exists(_.id == ClueId("clue_call_origin")))
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      reopened <- view(url,id)
      _ = assertEquals(reopened,pending)
      _ <- waitFor(url,id,10)
      traced <- view(url,id)
      _ = assert(traced.clues.find(_.id == ClueId("clue_call_origin")).get.text.contains("尚未确认实际使用电话的人"))
      _ = assert(!traced.clues.exists(_.id == ClueId("clue_call_lodge_scene")))
      _ <- request(url,id,"call_lodge_inspection")
      _ <- waitFor(url,id,5)
      sitePending <- view(url,id)
      _ = assert(!sitePending.clues.exists(_.id == ClueId("clue_call_lodge_phone")))
      _ <- waitFor(url,id,5)
      saved <- view(url,id)
      _ = assert(saved.clues.find(_.id == ClueId("clue_call_lodge_scene")).get.text.contains("本次查访时"))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_call_lodge_phone")))
      _ = assertEquals(saved.clues.find(_.id == ClueId("clue_call")).get,original)
      _ = assertEquals(saved.journal.count(_.clueIds(ClueId("clue_call_origin"))),1)
      _ = assert(!saved.stage.get.completed)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      retry <- tx(url)(command.plan)
      _ = assertEquals(restored,saved)
      _ = assertEquals(retry,accepted)
    yield ()).unsafeRunSync()
  }

  test("police receive completed results; witnesses need the actual report and caller truth is never a public conclusion") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val clement=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(input.user.contains("北门房"))
        assert(!input.user.contains("伪装女声"))
        assert(!input.user.contains("参与这宗命案的共谋"))
        response("clement_call_origin_reply")
      }
    (for
      _ <- setup(url,id)
      _ <- completed(url,id)
      _ <- ask(url,id,"melchett",client("call_lodge_phone"))
      _ <- ask(url,id,"clement",client("clement_call_origin_reply"))
      _ <- ask(url,id,"redding",client("redding_call_origin_reply"))
      notShown <- view(url,id)
      _ = assertEquals(notShown.interviews.takeRight(3).map(_.mode),Vector(InterviewReplyMode.Answered,InterviewReplyMode.Rejected,InterviewReplyMode.Rejected))
      _ <- ask(url,id,"clement",clement,Set(ClueId("clue_call_origin")))
      _ <- ask(url,id,"redding",client("redding_call_origin_reply"))
      isolated <- view(url,id)
      _ = assertEquals(isolated.interviews.last.mode,InterviewReplyMode.Rejected)
      _ <- read(url,id,"point_redding","route_redding_call_origin_reply")
      _ <- read(url,id,"point_police","route_police_call_review")
      saved <- view(url,id)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_clement_call_origin_reply")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_call_origin_reply")))
      _ = assert(!saved.clues.exists(_.id == ClueId("clue_police_call_review")))
      _ = assert(saved.police.get.exchanges.nonEmpty)
      _ = assert(!saved.points.find(_.id == PointId("point_police")).get.routes.exists(_.id == RouteId("route_police_call_review")))
      _ = assert(Set("clue_call","clue_call_origin","clue_call_lodge_scene","clue_call_lodge_phone").map(ClueId.apply).subsetOf(saved.police.get.sharedClues))
      _ = assert(!saved.asJson.noSpaces.contains("伪装女声"))
      _ = assert(!saved.asJson.noSpaces.contains("privateContext"))
    yield ()).unsafeRunSync()
  }

  test("revision 13 keeps its original reenactment and receipt without new call checks") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(13),content=LegacyFixture.withoutCallChecks(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield () }
      _ <- read(url,id,"point_clement","route_call")
      saved <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(saved.caseRevision,Revision(13))
      _ = assertEquals(saved.externalChecks.size,5)
      denied <- request(url,id,"call_origin").attempt
      _ = assertEquals(code(denied),Some(ErrorCode.AccessDenied))
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
    yield ()).unsafeRunSync()
  }
