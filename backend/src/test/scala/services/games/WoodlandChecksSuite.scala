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

class WoodlandChecksSuite extends FunSuite:
  private val owner=UserId("woodland-owner")
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
    _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,WitnessId(who),"树林样本核查有什么结果？",evidence).run(url,model)
  yield ()
  private def response(answer: String)=s"""{"reply":"我按目前记录说明。","disclosureIds":["$answer"]}"""
  private def client(answer: String)=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO.pure(s"""{"reply":"我按目前记录说明。","disclosureIds":["$answer"]}""")
  private def code(value: Either[Throwable,?])=value.left.toOption.collect { case e: GameError => e.code }
  private def basis(url: String,id: SessionId): IO[Unit]=
    read(url,id,"point_mary","route_mary_sound") *> read(url,id,"point_mary","route_mary_fish_time") *>
      read(url,id,"point_marple","route_marple_sound") *> read(url,id,"point_police","route_police_sound_check")
  private def completed(url: String,id: SessionId): IO[Unit]=
    basis(url,id) *> request(url,id,"woodland_search") *> waitFor(url,id,15) *>
      request(url,id,"crystal_opinion") *> waitFor(url,id,10)

  test("heard accounts must reach police; neither a sample nor an opinion exists before its check completes") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val pending=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(!input.user.contains("苦味酸"))
        response("crystal_opinion")
      }
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assertEquals(initial.caseRevision,Revision(16))
      early <- request(url,id,"woodland_search").attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"point_mary","route_mary_sound")
      _ <- read(url,id,"point_mary","route_mary_fish_time")
      _ <- read(url,id,"point_marple","route_marple_sound")
      unshared <- request(url,id,"woodland_search").attempt
      _ = assertEquals(code(unshared),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"point_police","route_police_sound_check")
      _ <- request(url,id,"woodland_search")
      _ <- waitFor(url,id,5)
      _ <- ask(url,id,"melchett",pending)
      progress <- view(url,id)
      _ = assertEquals(progress.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assert(!progress.clues.exists(_.id == ClueId("clue_woodland_sample")))
      opinion <- request(url,id,"crystal_opinion").attempt
      _ = assertEquals(code(opinion),Some(ErrorCode.ConditionFailed))
      _ <- waitFor(url,id,10)
      _ <- request(url,id,"crystal_opinion")
      _ <- waitFor(url,id,5)
      unfinished <- view(url,id)
      _ = assert(!unfinished.clues.exists(_.id == ClueId("clue_crystal_opinion")))
      _ <- waitFor(url,id,5)
      done <- view(url,id)
      _ = assert(done.clues.find(_.id == ClueId("clue_crystal_opinion")).get.text.contains("并非化验确认"))
    yield ()).unsafeRunSync()
  }

  test("search request rollback, exact retry and file-backed pending/completed restoration preserve original accounts") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("woodland-checks-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- basis(url,id)
      first <- view(url,id)
      original=first.clues
      command=RequestExternalCheckMessage(owner,id,RequestId("search-woods"),first.sequence,ExternalCheckId("woodland_search"))
      failed <- tx(url)(c => command.plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(failed.isLeft)
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,first)
      accepted <- tx(url)(command.plan)
      repeated <- tx(url)(command.plan)
      _ = assertEquals(repeated,accepted)
      _ <- waitFor(url,id,5)
      pending <- view(url,id)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      reopened <- view(url,id)
      _ = assertEquals(reopened,pending)
      _ <- waitFor(url,id,10)
      _ <- request(url,id,"crystal_opinion")
      _ <- waitFor(url,id,10)
      _ <- read(url,id,"point_police","route_police_woodland_review")
      saved <- view(url,id)
      _ = assertEquals(saved.clues.filter(c => original.exists(_.id == c.id)),original)
      _ = assertEquals(saved.journal.count(_.clueIds(ClueId("clue_woodland_sample"))),1)
      _ = assert(saved.police.get.sharedClues(ClueId("clue_crystal_opinion")))
      _ = assert(!saved.stage.get.completed)
      _ = assert(!saved.asJson.noSpaces.contains("搬石头"))
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      retry <- tx(url)(command.plan)
      _ = assertEquals(restored,saved)
      _ = assertEquals(retry,accepted)
    yield ()).unsafeRunSync()
  }

  test("completed results reach police but are not inserted into a regular witness context") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val police=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(input.user.contains("并非化验确认"))
        response("crystal_opinion")
      }
    val mary=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(!input.user.contains("苦味酸"))
        response("mary_sound")
      }
    (for
      _ <- setup(url,id)
      _ <- completed(url,id)
      _ <- ask(url,id,"melchett",police)
      _ <- ask(url,id,"mary",mary)
      saved <- view(url,id)
      _ = assert(saved.interviews.takeRight(2).forall(_.mode == InterviewReplyMode.Answered))
    yield ()).unsafeRunSync()
  }

  test("revision 14 keeps its seven checks and original receipt when reopened on the latest server") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(14),content=LegacyFixture.withoutWoodland(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield () }
      _ <- read(url,id,"point_mary","route_mary_sound")
      saved <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(saved.caseRevision,Revision(14))
      _ = assertEquals(saved.externalChecks.size,7)
      denied <- request(url,id,"woodland_search").attempt
      _ = assertEquals(code(denied),Some(ErrorCode.AccessDenied))
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
    yield ()).unsafeRunSync()
  }
