package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.games.api.*
import services.games.objects.*
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class AnneMeetingSuite extends FunSuite:
  private val owner=UserId("anne-meeting-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(run: java.sql.Connection => IO[A]): IO[A] =
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(run(c)))
  private def setup(url: String,id: SessionId): IO[Unit] = DatabaseSession.connection(url).use { c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)).void
  }
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def readAccount(id: SessionId)=InvestigateMessage(owner,id,RequestId("account"),Sequence(0),PointId("point_redding"),RouteId("route_redding_account"))
  private def meet(id: SessionId,seq: Long=1,request: String="meeting")=
    ExecuteOperationMessage(owner,id,RequestId(request),Sequence(seq),OperationId("open_anne_meeting"))
  private def ask(id: SessionId,witness: String,seq: Long,request: String="ask",evidence: Set[ClueId]=Set.empty)=
    AskWitnessMessage(owner,id,RequestId(request),Sequence(seq),WitnessId(witness),"你想说明什么？",evidence)
  private def client(raw: String,calls: AtomicInteger=new AtomicInteger())=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO { calls.incrementAndGet();raw }
  private def code(value: Either[Throwable,?])=value.left.toOption.collect { case e: GameError => e.code }
  private val statement="""{"reply":"是我杀了丈夫，我昨天开了枪。","disclosureIds":["anne_statement"]}"""

  test("Anne's statement and model context cannot be obtained before the explicit meeting") {
    val url=database;val id=SessionId(UUID.randomUUID().toString);val calls=new AtomicInteger()
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assert(!initial.witnesses.exists(_.id == WitnessId("anne")))
      _ = assertEquals(initial.people.find(_.id == PersonId("anne")).get.witness,None)
      _ = assert(!initial.asJson.noSpaces.contains("clue_anne_statement"))
      earlyMeeting <- tx(url)(meet(id,0).plan).attempt
      earlyRead <- tx(url)(InvestigateMessage(owner,id,RequestId("early"),Sequence(0),PointId("point_anne"),RouteId("route_anne_statement")).plan).attempt
      earlyQuestion <- ask(id,"anne",0).run(url,client(statement,calls)).attempt
      _ = assertEquals(code(earlyMeeting),Some(ErrorCode.ConditionFailed))
      _ = assertEquals(code(earlyRead),Some(ErrorCode.AccessDenied))
      _ = assertEquals(code(earlyQuestion),Some(ErrorCode.AccessDenied))
      _ = assertEquals(calls.get(),0)
      after <- view(url,id)
      _ = assertEquals(after,initial)
    yield ()).unsafeRunSync()
  }

  test("meeting opens after Redding's statement, is retryable and does not itself create a confession") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- tx(url)(readAccount(id).plan)
      before <- view(url,id)
      _ = assert(before.points.find(_.id == PointId("point_police")).get.operations.exists(_.id == OperationId("open_anne_meeting")))
      result <- tx(url)(meet(id).plan)
      retry <- tx(url)(meet(id).plan)
      repeated <- tx(url)(meet(id,2,"another-meeting").plan).attempt
      after <- view(url,id)
      _ = assertEquals(result,retry)
      _ = assertEquals(code(repeated),Some(ErrorCode.ConditionFailed))
      _ = assert(after.witnesses.exists(_.id == WitnessId("anne")))
      _ = assertEquals(after.people.find(_.id == PersonId("anne")).get.witness,Some(WitnessId("anne")))
      _ = assert(!after.clues.exists(_.id == ClueId("clue_anne_statement")))
      _ = assertEquals(after.sequence,Sequence(2))
    yield ()).unsafeRunSync()
  }

  test("actual Anne statement, explicit police presentation and meeting availability survive file reopen") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("anne-stage-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    val summary="""{"reply":"两份陈述的时间不同，我已保留，具体经过还需核查。","disclosureIds":["police_two_statements"]}"""
    (for
      _ <- setup(url,id)
      _ <- tx(url)(readAccount(id).plan)
      _ <- tx(url)(meet(id).plan)
      _ <- ask(id,"anne",2).run(url,client(statement))
      _ <- ask(id,"melchett",3,"not-shown").run(url,client(summary))
      before <- view(url,id)
      _ = assertEquals(before.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assert(before.clues.find(_.id == ClueId("clue_anne_statement")).get.text.contains("她的陈述"))
      _ <- ask(id,"melchett",4,"actually-shown",Set(ClueId("clue_anne_statement"),ClueId("clue_redding_account"))).run(url,client(summary))
      saved <- view(url,id)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_police_two_statements")))
      _ = assertEquals(saved.police.get.sharedClues,Set(ClueId("clue_anne_statement"),ClueId("clue_redding_account")))
      _ = assertEquals(saved.stage.get.completed,false)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
      _ = assert(restored.witnesses.exists(_.id == WitnessId("anne")))
      _ = assert(!restored.asJson.noSpaces.contains("privateContext"))
    yield ()).unsafeRunSync()
  }

  test("Redding reacts only to an actually shown statement and retains the recorded first version") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val reply="""{"reply":"她不可能干这种事。我已经作过供述。","disclosureIds":["redding_anne_reply"]}"""
    (for
      _ <- setup(url,id)
      _ <- tx(url)(readAccount(id).plan)
      _ <- tx(url)(meet(id).plan)
      _ <- ask(id,"anne",2).run(url,client(statement))
      _ <- ask(id,"redding",3,"not-shown").run(url,client(reply))
      _ <- ask(id,"redding",4,"shown",Set(ClueId("clue_anne_statement"))).run(url,client(reply))
      saved <- view(url,id)
      _ = assertEquals(saved.interviews(1).mode,InterviewReplyMode.Rejected)
      _ = assertEquals(saved.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_account")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_anne_reply")))
      _ = assert(!saved.asJson.noSpaces.contains("clue_redding_revised"))
    yield ()).unsafeRunSync()
  }
