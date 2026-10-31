package services.games

import cats.effect.{IO,Deferred,Ref}
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
import services.games.utils.{ReasoningRules,InterviewRules}
import services.games.tables.ReasoningRequestTable.ReasoningRequestTable
import services.games.tables.SessionTable.SessionTable
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class ReasoningSuite extends FunSuite:
  private val owner=UserId("reasoning-owner")
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
  private val both=Set(PersonId("anne"),PersonId("redding"))
  private val evidence=Set(ClueId("clue_call"))
  private val explanation="  这是我对责任人物、行动经过、时间冲突和材料联系的初步解释。\n"
  private def command(id: SessionId,sequence: Sequence,request: String=UUID.randomUUID().toString,suspects: Set[PersonId]=both,text: String=explanation)=
    EvaluateReasoningMessage(owner,id,RequestId(request),sequence,suspects,evidence,text)
  private def code(value: Either[Throwable,?])=value.left.toOption.collect { case e: GameError => e.code }
  private def model(established: Boolean,checkInput: ChatCompletionInput => Unit = _ => ()): ChatCompletionClient=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO {
      checkInput(input)
      val c=io.circe.parser.parse(input.user).toOption.get.hcursor
      c.get[String]("task").toOption.get match
        case "reasoning_judge" =>
          val ids=c.get[Vector[io.circe.Json]]("criteria").toOption.get.map(_.hcursor.get[String]("id").toOption.get)
          io.circe.Json.obj("checks" -> io.circe.Json.fromValues(ids.map(id => io.circe.Json.obj("id" -> io.circe.Json.fromString(id),"satisfied" -> io.circe.Json.fromBoolean(established)))),"evidenceIds" -> Vector("clue_call").asJson).noSpaces
        case "reasoning_guidance" =>
          io.circe.Json.obj("feedback" -> io.circe.Json.fromString(if established then "你的核心解释已有支持，可以选择结案，也可以继续核实。" else "请先解释这份来电记录怎样支持你的行动时间推断，再核实尚未说明的环节。"),"evidenceIds" -> Vector("clue_call").asJson).noSpaces
    }
  private def evaluate(url: String,id: SessionId,client: ChatCompletionClient,text: String=explanation): IO[Unit]=for
    v <- view(url,id)
    _ <- command(id,v.sequence,text=text).run(url,client)
  yield ()
  private def reveal(url: String,id: SessionId,abandon: Boolean): IO[Unit]=for
    v <- view(url,id)
    _ <- tx(url)(RevealReasoningMessage(owner,id,RequestId(UUID.randomUUID().toString),v.sequence,abandon).plan)
  yield ()

  test("only actual evidence can be assessed; hidden truth and legacy immediate-reveal bypass never enter an unfinished view") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val never=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.raiseError(new AssertionError("Must not call model"))
    (for
      _ <- setup(url,id)
      first <- view(url,id)
      _ = assertEquals(first.caseRevision,Revision(17))
      _ = assert(first.reasoning.nonEmpty && first.reasoning.get.attempts.isEmpty)
      _ = assert(!first.asJson.noSpaces.contains("预藏枪"))
      _ = assert(!first.asJson.noSpaces.contains("authorTruth"))
      denied <- command(id,first.sequence).run(url,never).attempt
      _ = assertEquals(code(denied),Some(ErrorCode.AccessDenied))
      foreign <- command(id,first.sequence).copy(userId=UserId("foreign")).run(url,never).attempt
      _ = assertEquals(code(foreign),Some(ErrorCode.AccessDenied))
      early <- reveal(url,id,false).attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"point_clement","route_call")
      v <- view(url,id)
      bypass <- tx(url)(SubmitConclusionMessage(owner,id,RequestId("old-api"),v.sequence,both,evidence,explanation).plan).attempt
      _ = assertEquals(code(bypass),Some(ErrorCode.AccessDenied))
      bad <- command(id,v.sequence,text="太短").run(url,never).attempt
      _ = assertEquals(code(bad),Some(ErrorCode.InvalidValue))
      after <- view(url,id)
      _ = assertEquals(after,v)
    yield ()).unsafeRunSync()
  }

  test("judge knows truth, guide is blind; revisions are append-only, exact retries make no further model calls, and successful assessment does not reveal review") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      calls <- Ref.of[IO,Int](0)
      _ <- setup(url,id)
      _ <- read(url,id,"point_clement","route_call")
      first <- view(url,id)
      inspect=(input: ChatCompletionInput) => {
        if input.user.contains("reasoning_judge") then
          assert(input.user.contains("authorTruth"));assert(input.user.contains("预藏枪"))
          assert(input.system.contains("同义改写"))
        else
          assert(!input.user.contains("authorTruth"));assert(!input.user.contains("预藏枪"))
          assert(!input.user.contains("authorCulprits"));assert(!input.user.contains("obtainableEvidence"))
          assert(input.user.contains("publicPeople"));assert(input.user.contains("牧师"))
          assert(input.system.contains("不能先写进问题当成前提"))
      }
      underlying=model(false,inspect)
      counted=new ChatCompletionClient:
        override def complete(input: ChatCompletionInput): IO[String]=calls.update(_+1) *> underlying.complete(input)
      original=command(id,first.sequence,"original")
      accepted <- original.run(url,counted)
      repeated <- original.run(url,counted)
      n <- calls.get
      _ = assertEquals(n,2)
      _ = assertEquals(accepted,repeated)
      revised <- view(url,id)
      _ = assertEquals(revised.reasoning.get.attempts.last.status,ReasoningStatus.Revise)
      _ = assertEquals(revised.reasoning.get.attempts.last.explanation,explanation)
      _ = assertEquals(revised.clues,first.clues)
      _ = assert(revised.conclusion.isEmpty && revised.reasoning.get.ending.isEmpty)
      _ = assert(!revised.asJson.noSpaces.contains("作者复盘"))
      early <- reveal(url,id,false).attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"point_mary","route_mary_sound")
      _ <- evaluate(url,id,model(true,inspect),"换一种说法重新解释人物分工、时间与已有材料之间的关系。")
      passed <- view(url,id)
      _ = assertEquals(passed.reasoning.get.attempts.size,2)
      _ = assertEquals(passed.reasoning.get.attempts.head,revised.reasoning.get.attempts.head)
      _ = assertEquals(passed.reasoning.get.attempts.last.status,ReasoningStatus.Established)
      _ = assert(passed.reasoning.get.ending.isEmpty && passed.conclusion.isEmpty)
      ending=RevealReasoningMessage(owner,id,RequestId("finish-case"),passed.sequence,false)
      a <- tx(url)(ending.plan)
      b <- tx(url)(ending.plan)
      _ = assertEquals(a,b)
      done <- view(url,id)
      _ = assertEquals(done.reasoning.get.ending.get.mode,ReasoningEndingMode.Solved)
      _ = assertEquals(done.clues,passed.clues)
      _ = assert(done.police.get.sharedClues.isEmpty)
      _ = assert(!done.stage.get.completed)
    yield ()).unsafeRunSync()
  }

  test("transport failures, forged success, injected extra fields and unknown guidance references are saved as non-verdicts, never as success or raw provider output") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val unavailable=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.raiseError(new RuntimeException("PRIVATE-SUPPLIER-ERROR"))
    val malformed=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.pure("""{"checks":[],"evidenceIds":[],"review":"SECRET-ANSWER"}""")
    val forged=model(true)
    val unknown=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=
        if input.user.contains("reasoning_judge") then model(false).complete(input)
        else IO.pure("""{"feedback":"PRIVATE-SECRET","evidenceIds":["not-earned"]}""")
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"point_clement","route_call")
      _ <- evaluate(url,id,unavailable)
      _ <- evaluate(url,id,malformed)
      v <- view(url,id)
      _ <- command(id,v.sequence,suspects=Set(PersonId("mary"))).run(url,forged)
      _ <- evaluate(url,id,unknown)
      saved <- view(url,id)
      _ = assertEquals(saved.reasoning.get.attempts.map(_.status),Vector(ReasoningStatus.Unavailable,ReasoningStatus.Rejected,ReasoningStatus.Rejected,ReasoningStatus.Rejected))
      _ = assert(saved.reasoning.get.attempts.forall(_.checks.isEmpty))
      _ = assert(!saved.asJson.noSpaces.contains("PRIVATE-"))
      _ = assert(!saved.asJson.noSpaces.contains("SECRET-ANSWER"))
      _ = assert(saved.reasoning.get.ending.isEmpty)
    yield ()).unsafeRunSync()
  }

  test("waiting holds no database connection or session lock; cross-protocol IDs and stale completions cannot overwrite newer investigation") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      started <- Deferred[IO,Unit]
      released <- Deferred[IO,Unit]
      _ <- setup(url,id)
      _ <- read(url,id,"point_clement","route_call")
      first <- view(url,id)
      delayed=new ChatCompletionClient:
        override def complete(input: ChatCompletionInput): IO[String]=
          if input.user.contains("reasoning_judge") then started.complete(()).void *> released.get *> model(false).complete(input)
          else model(false).complete(input)
      cmd=command(id,first.sequence,"delayed")
      fiber <- cmd.run(url,delayed).attempt.start
      _ <- started.get
      repeated <- cmd.run(url,model(false)).attempt
      _ = assertEquals(code(repeated),Some(ErrorCode.RequestPending))
      reused <- AskWitnessMessage(owner,id,cmd.requestId,first.sequence,WitnessId("mary"),"你听见了什么？",Set.empty).run(url,model(false)).attempt
      _ = assertEquals(code(reused),Some(ErrorCode.RequestConflict))
      _ <- read(url,id,"point_mary","route_mary_sound")
      current <- view(url,id)
      _ <- released.complete(())
      stale <- fiber.joinWithNever
      _ = assertEquals(code(stale),Some(ErrorCode.StaleState))
      after <- view(url,id)
      _ = assertEquals(after,current)
      _ = assert(after.reasoning.get.attempts.isEmpty)
    yield ()).unsafeRunSync()
  }

  test("expired reservations keep frozen truth; fencing, completion rollback, exact retries and real file reopen preserve one attempt") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("reasoning-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"point_clement","route_call")
      first <- view(url,id)
      cmd=command(id,first.sequence,"lease")
      prepared <- tx(url)(cmd.plan)
      old=prepared match
        case ReasoningPreparation.Reserved(r) => r
        case _ => fail("reservation required")
      _ <- tx(url)(c => ReasoningRequestTable.replace(c,old.copy(leaseUntilMillis=0),old.leaseToken))
      renewed <- tx(url)(cmd.plan)
      row=renewed match
        case ReasoningPreparation.Reserved(r) => r
        case _ => fail("renewal required")
      _ = assertEquals(row.context,old.context)
      raw <- model(false).complete(ReasoningRules.judgePrompt(row))
      decision=ReasoningRules.judge(raw,row)
      fenced <- tx(url)(FinishReasoningMessage(owner,id,cmd.requestId,old.leaseToken,decision,Some("请继续核实来电记录与时间线的关系。"),None).plan)
      _ = assertEquals(fenced.left.toOption.map(_.code),Some(ErrorCode.RequestPending))
      finish=FinishReasoningMessage(owner,id,cmd.requestId,row.leaseToken,decision,Some("请继续核实来电记录与时间线的关系。"),None)
      failed <- tx(url)(c => finish.plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(failed.isLeft)
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,first)
      result <- tx(url)(finish.plan)
      _ = assert(result.isRight)
      saved <- view(url,id)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      reopened <- view(url,id)
      repeated <- cmd.run(url,model(true))
      _ = assertEquals(repeated,result.toOption.get)
      _ = assertEquals(reopened,saved)
      _ = assertEquals(saved.reasoning.get.attempts.size,1)
      changed <- cmd.copy(explanation=explanation+"改变请求").run(url,model(false)).attempt
      _ = assertEquals(code(changed),Some(ErrorCode.RequestConflict))
    yield ()).unsafeRunSync()
  }

  test("explicit abandonment reveals the review without a successful assessment and cannot later be relabelled solved") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- reveal(url,id,true)
      saved <- view(url,id)
      _ = assertEquals(saved.reasoning.get.ending.get.mode,ReasoningEndingMode.Revealed)
      _ = assert(saved.reasoning.get.attempts.isEmpty)
      again <- reveal(url,id,false).attempt
      _ = assertEquals(code(again),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"point_clement","route_call")
      after <- view(url,id)
      denied <- command(id,after.sequence).run(url,model(true)).attempt
      _ = assertEquals(code(denied),Some(ErrorCode.ConditionFailed))
      state <- tx(url)(SessionTable.find(_,id)).map(_.get)
      revision <- tx(url)(CaseRevisionTable.find(_,state.caseId,state.caseRevision)).map(_.get)
      npc=InterviewRules.context(revision.content,state,WitnessId("mary"),"你知道什么？",Set.empty,Vector.empty,Vector.empty).toOption.get
      _ = assert(!npc.asJson.noSpaces.contains("authorTruth"))
      _ = assert(!npc.asJson.noSpaces.contains("预藏枪"))
    yield ()).unsafeRunSync()
  }

  test("revision 16 keeps its original public review and receipt when a pre-reasoning-column database is reopened") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(16),content=LegacyFixture.withoutEvaluation(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield () }
      _ <- read(url,id,"point_clement","route_call")
      first <- view(url,id)
      original=SubmitConclusionMessage(owner,id,RequestId("legacy-submit"),first.sequence,both,evidence,explanation)
      accepted <- tx(url)(original.plan)
      before <- view(url,id)
      _ <- DatabaseSession.connection(url).use(c => IO.blocking {
        val s=c.createStatement()
        try s.execute("ALTER TABLE game_sessions DROP COLUMN reasoning_json") finally s.close()
      } *> DatabaseSchema.initialize(c))
      reopened <- tx(url)(OpenStudyMessage(owner,id).plan)
      repeated <- tx(url)(original.plan)
      _ = assertEquals(reopened,before)
      _ = assertEquals(repeated,accepted)
      _ = assert(reopened.reasoning.isEmpty && reopened.conclusion.nonEmpty)
    yield ()).unsafeRunSync()
  }
