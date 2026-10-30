package services.games

import cats.effect.{Deferred,IO}
import cats.effect.unsafe.implicits.global
import io.circe.{Json,parser}
import java.nio.file.{Files,Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import munit.FunSuite
import scala.concurrent.duration.*
import services.accounts.tables.LocalIdentityTable.LocalIdentityTableInitializer
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.games.api.*
import services.games.objects.*
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.tables.ReceiptTable.ReceiptTable
import services.games.utils.InterviewRules
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class InterviewSuite extends FunSuite:
  private val owner=UserId("interview-owner")
  private def database = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(run: java.sql.Connection => IO[A]): IO[A] =
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(run(c)))
  private def setup(url: String,id: SessionId): IO[Unit] = DatabaseSession.connection(url).use { c =>
    DatabaseSchema.initialize(c) *> LocalIdentityTableInitializer.initialize(c) *>
      DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *>
        OpenStudyMessage(owner,id).plan(c)).void
  }
  private def client(answer: String,calls: AtomicInteger = new AtomicInteger()): ChatCompletionClient = new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String] = IO {
      calls.incrementAndGet()
      val old=parser.parse(answer).toOption.flatMap(_.asObject).filter(_.keys.toSet == Set("answerIds"))
      if !input.system.contains("语义问询匹配器") && old.nonEmpty then
        val ids=old.get("answerIds").get.as[Vector[String]].toOption.get
        val choices=parser.parse(input.user).toOption.get.hcursor.get[Vector[Json]]("disclosures").toOption.get
        val text=ids.flatMap(id => choices.find(_.hcursor.get[String]("id")==Right(id)).flatMap(_.hcursor.get[String]("text").toOption)).mkString("\n")
        Json.obj("reply" -> Json.fromString(if text.nonEmpty then text else "这件事我也不清楚。"),"disclosureIds" -> old.get("answerIds").get).noSpaces
      else answer
    }
  private def ask(id: SessionId,request: String="question",seq: Long=0,witness: String="clement",question: String="你家的钟走得准不准？",evidence: Set[ClueId]=Set.empty) =
    AskWitnessMessage(owner,id,RequestId(request),Sequence(seq),WitnessId(witness),question,evidence)
  private val habit="""{"answerIds":["clement_habit"]}"""

  test("natural replies can explain known people and personal experience without awarding a clue") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String] = IO {
        val prompt=parser.parse(input.user).toOption.get
        assert(input.system.contains("不是资料检索器"))
        assert(input.user.contains("阿博特"))
        assert(input.user.contains("17:30"))
        assert(input.user.contains("斯莱克"))
        assert(!input.user.contains("mary_time_basis"))
        assert(!input.user.contains("一声奇怪的砰响"))
        """{"reply":"斯莱克是调查这件案子的警探，昨晚来过寓所。我跟他说座钟的事，他根本不肯听。","disclosureIds":[]}"""
      }
    (for
      _ <- setup(url,id)
      _ <- ask(id,question="斯莱克是谁？").run(url,model)
      saved <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(saved.interviews.head.mode,InterviewReplyMode.Answered)
      _ = assert(saved.interviews.head.reply.startsWith("斯莱克是"))
      _ = assertEquals(saved.clues.size,0)
    yield ()).unsafeRunSync()
  }

  test("Mary receives her own sound memory and later departure, not another person's history") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String] = IO {
        assert(input.user.contains("确实听过一次奇怪的砰响"))
        assert(input.user.contains("确实离开寓所"))
        assert(!input.user.contains("阿博特太太"))
        """{"reply":"我听过一声响，当时以为在树林里。几点我没留意；后来我还出去请了医生。","disclosureIds":[]}"""
      }
    (setup(url,id) *> ask(id,witness="mary",question="你一直在家吗，有听到异响吗？").run(url,model)).unsafeRunSync()
  }

  test("police use the model, disclose medical clues and atomically remember actually shown evidence") {
    val url=database; val id=SessionId(UUID.randomUUID().toString); val calls=new AtomicInteger()
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String] = IO {
        calls.incrementAndGet()
        assert(input.user.contains("梅尔切特"))
        assert(input.user.contains("clue_clock"))
        assert(!input.user.contains("阿博特太太"))
        """{"reply":"医生认为是头部近距离枪击。他到场时估计已经死亡半小时左右，具体钟点还要核实。你这份座钟记录，我也看到了。","disclosureIds":["police_medical"]}"""
      }
    (for
      _ <- setup(url,id)
      _ <- tx(url)(InvestigateMessage(owner,id,RequestId("clock"),Sequence(0),PointId("point_clock"),RouteId("route_clock")).plan)
      command = ask(id,seq=1,witness="melchett",question="医生怎么看？请看这份记录。",evidence=Set(ClueId("clue_clock")))
      first <- command.run(url,model)
      retry <- command.run(url,model)
      saved <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(calls.get(),1)
      _ = assertEquals(first,retry)
      _ = assertEquals(saved.police.get.exchanges.size,1)
      _ = assertEquals(saved.police.get.sharedClues,Set(ClueId("clue_clock")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_medical_time")))
      _ = assertEquals(saved.interviews.head.witness,WitnessId("melchett"))
      _ = assertEquals(saved.police.get.exchanges.head.reply,saved.interviews.head.reply)
      _ = assertEquals(saved.sequence,Sequence(2))
    yield ()).unsafeRunSync()
  }

  test("police and witnesses cannot grant each other's disclosures or render invalid structured output") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- ask(id,witness="melchett").run(url,client("""{"reply":"DO_NOT_RENDER_SECRET","disclosureIds":["clement_habit"]}"""))
      _ <- ask(id,"extra",1).run(url,client("""{"reply":"DO_NOT_RENDER_SECRET","disclosureIds":[],"execute":"arrest"}"""))
      saved <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assert(saved.interviews.forall(_.mode == InterviewReplyMode.Rejected))
      _ = assert(!saved.interviews.exists(_.reply.contains("DO_NOT_RENDER")))
      _ = assertEquals(saved.clues.size,0)
    yield ()).unsafeRunSync()
  }

  test("a reserved legacy matching request keeps its response protocol when resumed after upgrade") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    val legacy=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String] = IO {
        assert(input.system.contains("语义问询匹配器"))
        habit
      }
    (for
      _ <- setup(url,id)
      prepared <- tx(url)(ask(id).plan)
      row = prepared match
        case InterviewPreparation.Reserved(value) => value
        case _ => fail("Expected reserved request")
      old = row.copy(context=row.context.copy(responseVersion=1,profile=row.context.profile.copy(dossier=row.context.profile.dossier.copy(knowledge=Vector.empty))),leaseUntilMillis=0)
      _ <- tx(url)(InterviewRequestTable.replace(_,old,row.leaseToken))
      _ <- ask(id).run(url,legacy)
      saved <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(saved.interviews.head.mode,InterviewReplyMode.Answered)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_habit")))
    yield ()).unsafeRunSync()
  }

  test("free questions disclose only approved answers; exact retry does not call the model again") {
    val url=database; val id=SessionId(UUID.randomUUID().toString); val calls=new AtomicInteger()
    (for
      _ <- setup(url,id)
      result <- ask(id).run(url,client(habit,calls))
      retry <- ask(id).run(url,client(habit,calls))
      snapshot <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(calls.get(),1)
      _ = assertEquals(result,retry)
      _ = assertEquals(snapshot.sequence,Sequence(1))
      _ = assertEquals(snapshot.interviews.size,1)
      _ = assertEquals(snapshot.interviews.head.mode,InterviewReplyMode.Answered)
      _ = assert(snapshot.clues.exists(_.id==ClueId("clue_habit")))
      _ = assert(!snapshot.clues.exists(_.id==ClueId("clue_time_contradiction")))
      conflict <- ask(id,question="不同的问题").run(url,client(habit,calls)).attempt
      _ = assertEquals(conflict.left.toOption.collect { case e: GameError => e.code },Some(ErrorCode.RequestConflict))
    yield ()).unsafeRunSync()
  }

  test("composite witness answer grants time and its church-clock basis without scripted question order") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- ask(id,witness="mary",question="你是凭什么记住上校到达时间的？").run(url,client("""{"answerIds":["mary_time_basis"]}"""))
      view <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(view.clues.map(_.id).toSet,Set(ClueId("clue_arrival"),ClueId("clue_arrival_basis")))
      _ = assertEquals(view.sequence,Sequence(1))
    yield ()).unsafeRunSync()
  }

  test("player knowledge is not automatically shown to NPCs, and another witness cannot disclose it") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    val shown="""{"answerIds":["clement_shown_clock"]}"""
    (for
      _ <- setup(url,id)
      _ <- tx(url)(InvestigateMessage(owner,id,RequestId("clock"),Sequence(0),PointId("point_clock"),RouteId("route_clock")).plan)
      _ <- ask(id,seq=1).run(url,client(shown))
      rejected <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(rejected.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assertEquals(rejected.clues.size,1)
      _ <- ask(id,"show",2,evidence=Set(ClueId("clue_clock"))).run(url,client(shown))
      _ <- ask(id,"remember-shown",3).run(url,client(shown))
      remembered <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(remembered.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assertEquals(remembered.interviews.last.shownClues,Set.empty[ClueId])
      _ <- ask(id,"other-person",4,witness="mary").run(url,client(shown))
      view <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assert(view.clues.exists(_.id==ClueId("clue_habit")))
      _ = assertEquals(view.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assertEquals(view.interviews.last.shownClues,Set.empty[ClueId])
      bad <- ask(id,"unknown-evidence",5,evidence=Set(ClueId("future_report"))).run(url,client(shown)).attempt
      _ = assertEquals(bad.left.toOption.collect { case e: GameError => e.code },Some(ErrorCode.AccessDenied))
    yield ()).unsafeRunSync()
  }

  test("the prompt contains only this witness and evidence actually shown to this witness") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String] = IO {
        val json=parser.parse(input.user).toOption.get
        assertEquals(json.hcursor.downField("witness").get[String]("name"),Right("克莱门特牧师"))
        assert(!input.user.contains("mary_time_basis"))
        assert(!input.user.contains("18:20"))
        assert(!input.user.contains("time_contradiction"))
        assert(!input.user.contains("clement_shown_clock"))
        """{"answerIds":[]}"""
      }
    (setup(url,id) *> ask(id).run(url,model)).unsafeRunSync()
  }

  test("invalid or injected model text is never rendered; transport failures are saved as system interruptions") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- ask(id).run(url,client("""{"answerIds":["future"],"reply":"SECRET_INVENTED_CONFESSION"}"""))
      _ <- ask(id,"unconfigured",1).run(url,ChatCompletionClient.unconfigured)
      view <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(view.interviews.map(_.mode),Vector(InterviewReplyMode.Rejected,InterviewReplyMode.Unavailable))
      _ = assert(!view.interviews.exists(_.reply.contains("SECRET_INVENTED_CONFESSION")))
      _ = assert(view.clues.isEmpty)
      _ = assertEquals(view.sequence,Sequence(2))
    yield ()).unsafeRunSync()
  }

  test("network wait holds no session lock; a concurrent mutation makes the completion stale") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      entered <- Deferred[IO,Unit]
      release <- Deferred[IO,Unit]
      model = new ChatCompletionClient:
        override def complete(input: ChatCompletionInput): IO[String] = entered.complete(()).void *> release.get.as(habit)
      fiber <- ask(id).run(url,model).attempt.start
      _ <- entered.get.timeout(3.seconds)
      view <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan).timeout(2.seconds)
      _ = assertEquals(view.sequence,Sequence(0))
      _ <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("parallel"),Sequence(0),OperationId("open_drawer")).plan).timeout(2.seconds)
      _ <- release.complete(())
      result <- fiber.joinWithNever
      _ = assertEquals(result.left.toOption.collect { case e: GameError => e.code },Some(ErrorCode.StaleState))
      after <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(after.sequence,Sequence(1))
      _ = assert(after.clues.isEmpty && after.interviews.isEmpty)
      row <- tx(url)(InterviewRequestTable.find(_,id,RequestId("question")))
      _ = assertEquals(row.map(_.status),Some(InterviewRequestStatus.Cancelled))
    yield ()).unsafeRunSync()
  }

  test("pending duplicate does not call the model twice and cannot reuse its ID for a synchronous mutation") {
    val url=database; val id=SessionId(UUID.randomUUID().toString); val calls=new AtomicInteger()
    (for
      _ <- setup(url,id)
      entered <- Deferred[IO,Unit]
      release <- Deferred[IO,Unit]
      model = new ChatCompletionClient:
        override def complete(input: ChatCompletionInput): IO[String] = IO(calls.incrementAndGet()) *> entered.complete(()).void *> release.get.as(habit)
      fiber <- ask(id).run(url,model).start
      _ <- entered.get.timeout(3.seconds)
      duplicate <- ask(id).run(url,model).attempt
      collision <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("question"),Sequence(0),OperationId("open_drawer")).plan).attempt
      _ = assertEquals(duplicate.left.toOption.collect { case e: GameError => e.code },Some(ErrorCode.RequestPending))
      _ = assertEquals(collision.left.toOption.collect { case e: GameError => e.code },Some(ErrorCode.RequestConflict))
      _ <- release.complete(())
      completed <- fiber.joinWithNever
      retry <- ask(id).run(url,model)
      _ = assertEquals(completed,retry)
      _ = assertEquals(calls.get(),1)
    yield ()).unsafeRunSync()
  }

  test("expired reservation can resume after reopening the file database; old lease cannot finish it") {
    val directory=Files.createTempDirectory("witness-recovery-")
    val url=s"jdbc:h2:file:${directory.resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      prepared <- tx(url)(ask(id).plan)
      old = prepared match
        case InterviewPreparation.Reserved(row) => row
        case _ => throw new IllegalStateException("Expected reservation")
      _ <- tx(url)(c => InterviewRequestTable.replace(c,old.copy(leaseUntilMillis=0),old.leaseToken))
      entered <- Deferred[IO,Unit]
      release <- Deferred[IO,Unit]
      model = new ChatCompletionClient:
        override def complete(input: ChatCompletionInput): IO[String] = entered.complete(()).void *> release.get.as(habit)
      fiber <- ask(id).run(url,model).start
      _ <- entered.get.timeout(3.seconds)
      fenced <- tx(url)(FinishInterviewMessage(owner,id,old.requestId,old.leaseToken,Some(InterviewDecision(Vector(WitnessAnswerId("clement_habit")))),None).plan)
      _ = assertEquals(fenced.left.toOption.map(_.code),Some(ErrorCode.RequestPending))
      _ <- release.complete(())
      _ <- fiber.joinWithNever
      view <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(view.interviews.size,1)
      _ = assertEquals(view.sequence,Sequence(1))
      receipt <- tx(url)(ReceiptTable.find(_,id,old.requestId))
      _ = assert(receipt.nonEmpty)
    yield ()).unsafeRunSync()
  }

  test("a later receipt failure rolls back the conversation, state, log and completion together") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- DatabaseSession.connection(url).use(c => IO.blocking {
        val statement=c.createStatement()
        try statement.execute("ALTER TABLE game_receipts ADD CONSTRAINT reject_test_receipt CHECK (1=0)") finally statement.close()
      })
      result <- ask(id).run(url,client(habit)).attempt
      _ = assert(result.isLeft)
      view <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      row <- tx(url)(InterviewRequestTable.find(_,id,RequestId("question")))
      _ = assertEquals(view.sequence,Sequence(0))
      _ = assert(view.interviews.isEmpty && view.journal.isEmpty && view.clues.isEmpty)
      _ = assertEquals(row.map(_.status),Some(InterviewRequestStatus.Pending))
    yield ()).unsafeRunSync()
  }


  test("an explicit fixed evidence presentation is remembered by that witness in free conversation") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- tx(url)(InvestigateMessage(owner,id,RequestId("clock"),Sequence(0),PointId("point_clock"),RouteId("route_clock")).plan)
      _ <- tx(url)(InvestigateMessage(owner,id,RequestId("habit"),Sequence(1),PointId("point_clement"),RouteId("route_habit")).plan)
      _ <- tx(url)(InvestigateMessage(owner,id,RequestId("show"),Sequence(2),PointId("point_clement"),RouteId("route_clock_presented")).plan)
      _ <- ask(id,"remember",3).run(url,client("""{"answerIds":["clement_shown_clock"]}"""))
      view <- tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
      _ = assertEquals(view.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assertEquals(view.interviews.last.shownClues,Set.empty[ClueId])
    yield ()).unsafeRunSync()
  }
