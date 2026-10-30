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

class PaperChecksSuite extends FunSuite:
  private val owner=UserId("paper-owner")
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
    _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,WitnessId(who),"纸张检查有什么结果？",evidence).run(url,model)
  yield ()
  private def client(answer: String)=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO.pure(s"""{"reply":"我按目前记录说明。","disclosureIds":["$answer"]}""")
  private def code(value: Either[Throwable,?])=value.left.toOption.collect { case e: GameError => e.code }
  private def inspection(url: String,id: SessionId): IO[Unit]=
    read(url,id,"point_paper","route_paper") *> request(url,id,"paper_inspection") *> waitFor(url,id,10)

  test("paper excerpt authorizes only an initial inspection; results and expert conclusions cannot be guessed") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assertEquals(initial.caseRevision,Revision(13))
      _ = assert(initial.externalChecks.filter(_.id.value.startsWith("paper_")).forall(!_.available))
      early <- request(url,id,"paper_inspection").attempt
      expert <- request(url,id,"paper_handwriting").attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ = assertEquals(code(expert),Some(ErrorCode.ConditionFailed))
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,initial)
      _ <- read(url,id,"point_paper","route_paper")
      premature <- request(url,id,"paper_handwriting").attempt
      _ = assertEquals(code(premature),Some(ErrorCode.ConditionFailed))
      _ <- ask(url,id,"melchett",client("paper_handwriting"))
      rejected <- view(url,id)
      _ = assertEquals(rejected.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assertEquals(rejected.clues.map(_.id),Vector(ClueId("clue_paper")))
    yield ()).unsafeRunSync()
  }

  test("two inspections finish separately; original excerpt, pending state, exact retries and completed records survive reopening") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("paper-checks-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"point_paper","route_paper")
      first <- view(url,id)
      excerpt=first.clues.head
      command=RequestExternalCheckMessage(owner,id,RequestId("initial-inspection"),first.sequence,ExternalCheckId("paper_inspection"))
      failed <- tx(url)(c => command.plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(failed.isLeft)
      rolledBack <- view(url,id)
      _ = assertEquals(rolledBack,first)
      accepted <- tx(url)(command.plan)
      retry <- tx(url)(command.plan)
      _ = assertEquals(retry,accepted)
      _ <- waitFor(url,id,5)
      pending <- view(url,id)
      _ = assert(!pending.clues.exists(_.id == ClueId("clue_paper_ink")))
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      pendingRestored <- view(url,id)
      _ = assertEquals(pendingRestored,pending)
      _ <- waitFor(url,id,5)
      inspected <- view(url,id)
      _ = assert(inspected.clues.exists(_.id == ClueId("clue_paper_ink")))
      _ = assert(inspected.clues.exists(_.id == ClueId("clue_paper_fingerprints")))
      _ = assert(!inspected.clues.exists(_.id == ClueId("clue_paper_handwriting")))
      _ <- request(url,id,"paper_handwriting")
      _ <- waitFor(url,id,15)
      stillPending <- view(url,id)
      _ = assert(!stillPending.clues.exists(_.id == ClueId("clue_paper_handwriting")))
      _ <- waitFor(url,id,5)
      saved <- view(url,id)
      _ = assert(saved.clues.find(_.id == ClueId("clue_paper")).contains(excerpt))
      _ = assert(saved.clues.find(_.id == ClueId("clue_paper_handwriting")).get.text.contains("尚不能确定"))
      _ = assertEquals(saved.journal.count(_.clueIds(ClueId("clue_paper_handwriting"))),1)
      _ = assert(!saved.stage.get.completed)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      retried <- tx(url)(command.plan)
      _ = assertEquals(restored,saved)
      _ = assertEquals(retried,accepted)
    yield ()).unsafeRunSync()
  }

  test("police know only completed reports; other witnesses need actual presentation and the paper receipt needs all four materials") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val pendingModel=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(!input.user.contains("正文不是普罗瑟罗上校所写"))
        """{"reply":"目前还没有完成比对。","disclosureIds":[]}"""
      }
    (for
      _ <- setup(url,id)
      _ <- inspection(url,id)
      _ <- ask(url,id,"melchett",pendingModel)
      _ <- ask(url,id,"melchett",client("paper_ink"))
      _ <- ask(url,id,"mary",client("paper_ink"))
      _ <- ask(url,id,"mary",client("paper_ink"),Set(ClueId("clue_paper_ink")))
      known <- view(url,id)
      _ = assertEquals(known.interviews.takeRight(3).map(_.mode),Vector(InterviewReplyMode.Answered,InterviewReplyMode.Rejected,InterviewReplyMode.Answered))
      early <- read(url,id,"point_police","route_police_paper_review").attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ <- request(url,id,"paper_handwriting")
      _ <- waitFor(url,id,20)
      _ <- ask(url,id,"melchett",client("paper_handwriting"))
      _ <- ask(url,id,"melchett",client("police_paper_review"))
      unshared <- view(url,id)
      _ = assertEquals(unshared.interviews.last.mode,InterviewReplyMode.Rejected)
      _ <- read(url,id,"point_police","route_police_paper_review")
      saved <- view(url,id)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_police_paper_review")))
      _ = assert(Set("clue_paper","clue_paper_ink","clue_paper_fingerprints","clue_paper_handwriting").map(ClueId.apply).subsetOf(saved.police.get.sharedClues))
      _ = assert(!saved.asJson.noSpaces.contains("privateContext"))
    yield ()).unsafeRunSync()
  }

  test("revision 11 retains its original checks and original receipt after paper checks are published") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(11),content=LegacyFixture.withoutPaperChecks(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield () }
      _ <- read(url,id,"point_paper","route_paper")
      saved <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(saved.caseRevision,Revision(11))
      _ = assertEquals(saved.externalChecks.size,2)
      denied <- request(url,id,"paper_inspection").attempt
      _ = assertEquals(code(denied),Some(ErrorCode.AccessDenied))
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
    yield ()).unsafeRunSync()
  }
