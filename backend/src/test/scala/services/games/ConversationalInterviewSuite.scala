package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.games.api.*
import services.games.objects.*
import services.games.utils.InterviewRules
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class ConversationalInterviewSuite extends FunSuite:
  private val owner=UserId("conversation-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *>
    DatabaseSession.transaction(c)(      ImportCaseMessage(Paths.get("../docs/fixtures/conversational_investigation.json")).plan(c) *>
      StartGameMessage(owner,id,CaseId("vicarage_study")).plan(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,route: String)=for
    before <- view(url,id)
    point=before.points.find(_.routes.exists(_.id == RouteId(route))).get.id
    _ <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,point,RouteId(route)).plan)
  yield ()
  private def answer(reply: String,continuous: Boolean)=Json.obj("reply" -> reply.asJson,"disclosureIds" -> Json.arr())
    .deepMerge(if continuous then Json.obj("statementUpdates" -> Json.arr(),"focus" -> DialogueFocus(reply.take(160),None,false).asJson) else Json.obj()).noSpaces

  test("all available witnesses and police reuse the conversational prompt without receiving another character's private context") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      first <- view(url,id)
      _ <- first.witnesses.foldLeft(IO.unit) { (previous,witness) => previous *> (for
        before <- view(url,id)
        model=new ChatCompletionClient:
          override def complete(input: ChatCompletionInput): IO[String]=IO {
            assert(input.system.contains("反问要给玩家真实回答机会"))
            assert(input.system.contains("普通追问、换词"))
            val user=parser.parse(input.user).toOption.get
            assert(user.hcursor.get[Vector[Json]]("recordedStatements").isRight)
            assert(user.hcursor.get[Json]("authorTruth").isLeft)
            if witness.id != WitnessId("redding") then assert(!input.user.contains("你知道召走牧师的那通电话是你"))
            answer("你具体想问哪一件事？",user.hcursor.downField("statementOptions").succeeded)
          }
        _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,witness.id,"我想听你说明一些情况。",Set.empty).run(url,model)
      yield ()) }
      after <- view(url,id)
      _ = assert(after.interviews.forall(_.mode == InterviewReplyMode.Answered))
      _ = assert(after.clues.isEmpty)
    yield ()).unsafeRunSync()
  }

  test("a counterquestion is saved without granting a statement and its answer stays in the next frozen context") {
    val url=database;val id=SessionId(UUID.randomUUID().toString);val calls=new AtomicInteger()
    val questionModel=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO { calls.incrementAndGet();answer("你们为什么怀疑安妮？",true) }
    val nextModel=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val user=parser.parse(input.user).toOption.get.hcursor
        assertEquals(input.history.last.assistant,"你们为什么怀疑安妮？")
        assertEquals(user.downField("dialogueFocus").downField("lastSpokenExchange").get[String]("reply"),Right("你们为什么怀疑安妮？"))
        assertEquals(user.get[String]("question"),Right("这是我的猜测，警方没有告诉我她是否被排除。"))
        assert(user.get[Vector[Json]]("recordedStatements").toOption.get.exists(_.hcursor.get[String]("id")==Right("clue_redding_gun")))
        answer("只是你的猜测？那我先前说的没有什么要改的。",true)
      }
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_redding_account")
      _ <- read(url,id,"route_redding_gun")
      before <- view(url,id)
      command=AskWitnessMessage(owner,id,RequestId("counterquestion"),before.sequence,WitnessId("redding"),"安妮也认罪了，你要改口吗？",Set.empty)
      first <- command.run(url,questionModel)
      retry <- command.run(url,questionModel)
      _ = assertEquals(first,retry)
      _ = assertEquals(calls.get(),1)
      middle <- view(url,id)
      _ = assertEquals(middle.clues,before.clues)
      _ <- AskWitnessMessage(owner,id,RequestId("answer-counterquestion"),middle.sequence,WitnessId("redding"),"这是我的猜测，警方没有告诉我她是否被排除。",Set.empty).run(url,nextModel)
      request <- tx(url)(InterviewRequestTable.find(_,id,RequestId("answer-counterquestion")))
      recent <- tx(url)(InterviewRequestTable.recent(_,1))
      _ = assertEquals(recent.head.requestId,RequestId("answer-counterquestion"))
      _ = assertEquals(request.get.context.promptVersion,6)
      restored=request.get.asJson.as[InterviewRequestRecord].toOption.get
      _ = assertEquals(restored,request.get)
      after <- view(url,id)
      _ = assertEquals(after.clues,before.clues)
      _ = assertEquals(after.interviews.count(_.requestId == RequestId("counterquestion")),1)
    yield ()).unsafeRunSync()
  }

  test("fixed fallback cannot retract the gun story merely because its ordinary source question was asked") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_redding_account")
      _ <- read(url,id,"route_redding_gun")
      before <- view(url,id)
      _ = assert(!before.points.flatMap(_.routes).exists(_.id == RouteId("route_redding_gun_revised")))
      _ <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("meet"),before.sequence,OperationId("open_anne_meeting")).plan)
      _ <- read(url,id,"route_anne_statement")
      after <- view(url,id)
      _ = assert(!after.points.flatMap(_.routes).exists(_.id == RouteId("route_redding_revised")))
      _ = assert(after.followUps.isEmpty)
    yield ()).unsafeRunSync()
  }

  test("dialogue roles retain only this witness's spoken turns, skip failures and keep frozen template 3 unchanged") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    def model(reply: String)=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.pure(answer(reply,true))
    def ask(witness: String,question: String,client: ChatCompletionClient)=for
      before <- view(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,WitnessId(witness),question,Set.empty).run(url,client)
    yield ()
    (for
      _ <- setup(url,id)
      _ <- ask("mary","你知道什么？",model("玛丽独有的谈话。"))
      _ <- (0 until 13).foldLeft(IO.unit)((previous,n) => previous *> ask("redding",s"第${n}次追问",model(if n == 12 then "你是说第一支，还是第二支？" else s"答复$n")))
      _ <- ask("redding","刚才那支",new ChatCompletionClient:
        override def complete(input: ChatCompletionInput): IO[String]=IO.pure("invalid-json"))
      // Twelve attempts include the technical failure, so only eleven pairs remain.
      before <- view(url,id)
      prepared <- tx(url)(AskWitnessMessage(owner,id,RequestId("role-check"),before.sequence,WitnessId("redding"),"第一支",Set.empty).plan)
      saved=(prepared match
        case InterviewPreparation.Reserved(row) => row
        case _ => throw new IllegalStateException("Expected reservation"))
      input=InterviewRules.prompt(saved)
      _ = assertEquals(input.history.size,11)
      _ = assertEquals(parser.parse(input.history.head.user).toOption.get.hcursor.get[String]("question"),Right("第2次追问"))
      _ = assert(!input.history.exists(_.assistant.contains("玛丽独有")))
      _ = assert(!input.history.exists(_.assistant.contains("未通过检查")))
      _ = assertEquals(input.history.last.assistant,"你是说第一支，还是第二支？")
      focus=parser.parse(input.user).toOption.get.hcursor.downField("dialogueFocus")
      _ = assertEquals(focus.downField("latestAttempt").get[String]("mode"),Right("rejected"))
      _ = assertEquals(focus.downField("lastSpokenExchange").get[String]("reply"),Right("你是说第一支，还是第二支？"))
      old=InterviewRules.prompt(saved.copy(context=saved.context.copy(promptVersion=3,responseVersion=3,activeFocus=None)))
      _ = assert(old.history.isEmpty)
      _ = assert(parser.parse(old.user).toOption.get.hcursor.get[Vector[Json]]("history").isRight)
      _ = assert(!old.system.contains("对话承接规则"))
      frozenFour=InterviewRules.prompt(saved.copy(context=saved.context.copy(promptVersion=4,responseVersion=3,activeFocus=None)))
      _ = assertEquals(frozenFour.history,input.history)
      _ = assert(!frozenFour.system.contains("说话人与指代核对"))
      _ = assertEquals(InterviewRules.prompt(saved.asJson.as[InterviewRequestRecord].toOption.get),input)
    yield ()).unsafeRunSync()
  }


  test("clarification focus is saved with its source, survives a new connection and is consumed after the player clarifies") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val clarify="你是问我为什么去请医生吗？"
    val focus=DialogueFocus("去请医生",Some(clarify),true)
    val first=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.pure(Json.obj("reply" -> clarify.asJson,
        "disclosureIds" -> Json.arr(),"statementUpdates" -> Json.arr(),"focus" -> focus.asJson).noSpaces)
    val second=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val active=parser.parse(input.user).toOption.get.hcursor.get[RecordedDialogueFocus]("activeFocus").toOption.get
        assertEquals(active.focus,focus)
        assertEquals(active.sequence,Sequence(1))
        assert(input.history.isEmpty)
        answer("因为牧师让我去请海多克医生，我就去了。",true)
      }
    (for
      _ <- setup(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId("clarify"),Sequence(0),WitnessId("mary"),"为什么？",Set.empty).run(url,first)
      // A technical failure cannot erase the pending focus, even beyond the twelve-attempt window.
      _ <- (0 until 13).foldLeft(IO.unit)((previous,n) => previous *> (for
        state <- view(url,id)
        _ <- AskWitnessMessage(owner,id,RequestId(s"focus-failure-$n"),state.sequence,WitnessId("mary"),"对，我问的是去请医生。",Set.empty).run(url,new ChatCompletionClient:
          override def complete(input: ChatCompletionInput): IO[String]=IO.pure("invalid-json"))
      yield ()))
      before <- view(url,id)
      _ = assert(before.clues.isEmpty)
      _ <- AskWitnessMessage(owner,id,RequestId("explain"),before.sequence,WitnessId("mary"),"对，我问的是去请医生。",Set.empty).run(url,second)
      after <- view(url,id)
      prepared <- tx(url)(AskWitnessMessage(owner,id,RequestId("after-explanation"),after.sequence,WitnessId("mary"),"明白了。",Set.empty).plan)
      context=prepared match
        case InterviewPreparation.Reserved(row) => row.context
        case _ => throw new IllegalStateException("Expected reservation")
      _ = assertEquals(context.activeFocus.flatMap(_.focus.pendingQuestion),None)
      _ = assertEquals(context.activeFocus.map(_.sequence),Some(Sequence(15)))
      _ = assertEquals(context.asJson.as[InterviewContext],Right(context))
      _ = assertEquals(after.clues,before.clues)
      missing=Json.obj("reply" -> clarify.asJson,"disclosureIds" -> Json.arr(),"statementUpdates" -> Json.arr())
      _ = assertEquals(InterviewRules.decision(missing.noSpaces,context).flatMap(_.focus),Some(DialogueFocus(clarify,None,false)))
      _ = assertEquals(InterviewRules.decision(missing.deepMerge(Json.obj("focus" -> focus.copy(topic="没有说出的秘密").asJson)).noSpaces,context).flatMap(_.focus),Some(DialogueFocus(clarify,None,false)))
      _ = assertEquals(InterviewRules.decision(missing.deepMerge(Json.obj("focus" -> focus.copy(pendingQuestion=None).asJson)).noSpaces,context).flatMap(_.focus),Some(DialogueFocus(clarify,None,false)))
      _ = assert(InterviewRules.decision(missing.deepMerge(Json.obj("focus" -> focus.asJson,"disclosureIds" -> Vector("mary_sound").asJson)).noSpaces,context).isEmpty)
      legacy=context.copy(promptVersion=5,responseVersion=3,activeFocus=None)
      _ = assert(InterviewRules.decision(missing.noSpaces,legacy).nonEmpty)
      _ = assert(!legacy.asJson.hcursor.downField("activeFocus").succeeded)
    yield ()).unsafeRunSync()
  }

  test("invalid auxiliary focus falls back to reply text while authority and legacy checks stay strict") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val text="上校来寓所是要见牧师。"
    (for
      _ <- setup(url,id)
      prepared <- tx(url)(AskWitnessMessage(owner,id,RequestId("focus-validation"),Sequence(0),WitnessId("mary"),"为什么上校会出现在你的寓所？",Set.empty).plan)
      context=prepared match
        case InterviewPreparation.Reserved(row) => row.context
        case _ => throw new IllegalStateException("Expected reservation")
      base=Json.obj("reply" -> text.asJson,"disclosureIds" -> Json.arr(),"statementUpdates" -> Json.arr())
      badFocus=Vector(Json.Null,Json.fromString("概括"),Json.obj(),
        DialogueFocus("摘要而非原文",None,false).asJson,
        DialogueFocus("",None,false).asJson,
        DialogueFocus(text,Some("没有说过的问题？"),false).asJson,
        DialogueFocus(text,None,true).asJson,
        DialogueFocus(text,None,false).asJson.deepMerge(Json.obj("extra" -> true.asJson)))
      _ = (Vector(base) ++ badFocus.map(f => base.deepMerge(Json.obj("focus" -> f)))).foreach { raw =>
        val decision=InterviewRules.decision(raw.noSpaces,context).get
        assertEquals(decision.reply,Some(text))
        assertEquals(decision.focus,Some(DialogueFocus(text,None,false)))
        assert(InterviewRules.validDecision(decision,context))
      }
      _ = Vector(
        base.deepMerge(Json.obj("disclosureIds" -> Vector("not_allowed").asJson)),
        base.deepMerge(Json.obj("disclosureIds" -> Vector("mary_sound","mary_sound").asJson)),
        base.deepMerge(Json.obj("reply" -> "".asJson)),
        base.deepMerge(Json.obj("reply" -> ("长" * 2001).asJson)),
        base.deepMerge(Json.obj("statementUpdates" -> Json.arr(Json.obj("answerId" -> "mary_sound".asJson,"evidenceIds" -> Json.arr(),"text" -> text.asJson)))),
        base.deepMerge(Json.obj("extra" -> true.asJson)),
        base.deepMerge(Json.obj("disclosureIds" -> Vector("mary_sound").asJson,
          "focus" -> DialogueFocus("坏摘录",None,true).asJson)),
        base.mapObject(_.remove("statementUpdates")),
        base.mapObject(_.remove("disclosureIds"))
      ).foreach(raw => assert(InterviewRules.decision(raw.noSpaces,context).isEmpty))
      _ = assert(InterviewRules.decision(base.deepMerge(Json.obj("focus" -> Json.Null)).noSpaces,context.copy(responseVersion=3)).isEmpty)
      _ = assert(InterviewRules.decision(base.noSpaces,context.copy(responseVersion=3)).nonEmpty)
    yield ()).unsafeRunSync()
  }

  test("repaired focus persists with the accepted reply, is reused next turn and retries keep the original receipt") {
    val url=database;val id=SessionId(UUID.randomUUID().toString);val calls=new AtomicInteger()
    val text="上校来寓所是要见牧师。"
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        calls.incrementAndGet()
        Json.obj("reply" -> text.asJson,"disclosureIds" -> Json.arr(),"statementUpdates" -> Json.arr(),
          "focus" -> DialogueFocus("上校来访的原因",None,false).asJson).noSpaces
      }
    (for
      _ <- setup(url,id)
      before <- view(url,id)
      command=AskWitnessMessage(owner,id,RequestId("repaired-focus"),before.sequence,WitnessId("mary"),"为什么上校会出现在你的寓所？",Set.empty)
      first <- command.run(url,model)
      replay <- command.run(url,model)
      _ = assertEquals(first,replay)
      _ = assertEquals(calls.get(),1)
      after <- view(url,id)
      _ = assertEquals(after.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assertEquals(after.interviews.last.reply,text)
      _ = assertEquals(after.clues,before.clues)
      next <- tx(url)(AskWitnessMessage(owner,id,RequestId("after-repaired-focus"),after.sequence,WitnessId("mary"),"然后呢？",Set.empty).plan)
      row=next match
        case InterviewPreparation.Reserved(row) => row
        case _ => throw new IllegalStateException("Expected reservation")
      _ = assertEquals(row.context.activeFocus,Some(RecordedDialogueFocus(Sequence(1),DialogueFocus(text,None,false))))
      _ = assertEquals(row.asJson.as[InterviewRequestRecord],Right(row))
      _ = assertEquals(InterviewRules.prompt(row).history.last.assistant,text)
    yield ()).unsafeRunSync()
  }

  test("the common response format gives an ordinary witness no statement-update authority") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val text="我家的钟平时快十五分钟。"
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assertEquals(parser.parse(input.user).toOption.get.hcursor.get[Vector[String]]("statementOptions"),Right(Vector.empty[String]))
        Json.obj("reply" -> text.asJson,"disclosureIds" -> Vector("clement_habit").asJson,
          "statementUpdates" -> Vector(Json.obj("answerId" -> "clement_habit".asJson,
            "evidenceIds" -> Vector("clue_clock").asJson,"text" -> text.asJson)).asJson).noSpaces
      }
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_clock")
      before <- view(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId("invalid-update"),before.sequence,WitnessId("clement"),"钟准吗？",Set(ClueId("clue_clock"))).run(url,model)
      after <- view(url,id)
      _ = assertEquals(after.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assertEquals(after.clues,before.clues)
    yield ()).unsafeRunSync()
  }

  test("selected Anne and medical texts reach the frozen model input and are not confused with earlier materials") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val selected=Set("clue_anne_time","clue_anne_gun","clue_medical_time","clue_medical_visit_record").map(ClueId.apply)
    val firstModel=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val user=parser.parse(input.user).toOption.get.hcursor
        val materials=user.get[Vector[Json]]("presentedThisTurn").toOption.get
        assertEquals(materials.map(_.hcursor.get[String]("id").toOption.get).toSet,selected.map(_.value))
        assert(materials.exists(_.hcursor.get[String]("text").exists(_.contains("梳妆台"))))
        assert(materials.exists(_.hcursor.get[String]("text").exists(_.contains("半小时"))))
        assert(materials.exists(_.hcursor.get[String]("text").exists(_.contains("19:00"))))
        assert(input.system.contains("没有精确钟点不等于医生没有时间意见"))
        answer("这两份陈述我看见了；医生说的是检查时已死半小时左右。我仍坚持先前的说法。",true)
      }
    val laterModel=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val user=parser.parse(input.user).toOption.get.hcursor
        assertEquals(user.get[Vector[Json]]("presentedThisTurn"),Right(Vector.empty[Json]))
        assert(selected.map(_.value).subsetOf(user.get[Vector[Json]]("shownEvidence").toOption.get.map(_.hcursor.get[String]("id").toOption.get).toSet))
        answer("刚才你给的医生记录，我已经看过了。",true)
      }
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_redding_account")
      start <- view(url,id)
      _ <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("meet"),start.sequence,OperationId("open_anne_meeting")).plan)
      _ <- read(url,id,"route_anne_time")
      _ <- read(url,id,"route_anne_gun")
      _ <- read(url,id,"route_police_medical")
      checking <- view(url,id)
      _ <- tx(url)(RequestExternalCheckMessage(owner,id,RequestId("medical"),checking.sequence,ExternalCheckId("medical_timing")).plan)
      waiting <- view(url,id)
      _ <- tx(url)(AdvanceTimeMessage(owner,id,RequestId("wait"),waiting.sequence,15).plan)
      before <- view(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId("read-materials"),before.sequence,WitnessId("redding"),"安妮说了什么，医生意见与你的供述相符吗？",selected).run(url,firstModel)
      saved <- tx(url)(InterviewRequestTable.find(_,id,RequestId("read-materials")))
      _ = assertEquals(saved.get.context.promptVersion,6)
      _ = assertEquals(saved.get.context.caseId,CaseId("vicarage_study"))
      old=saved.get.copy(context=saved.get.context.copy(promptVersion=2,responseVersion=3,activeFocus=None))
      _ = assert(!parser.parse(InterviewRules.prompt(old).user).toOption.get.hcursor.downField("presentedThisTurn").succeeded)
      _ = assert(!InterviewRules.prompt(old).system.contains("实际材料的阅读规则"))
      next <- view(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId("recall"),next.sequence,WitnessId("redding"),"还记得刚才给你的材料吗？",Set.empty).run(url,laterModel)
    yield ()).unsafeRunSync()
  }

  test("a relative medical estimate does not acquire an unprovided examination timestamp") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val user=parser.parse(input.user).toOption.get.hcursor
        val material=user.get[Vector[Json]]("presentedThisTurn").toOption.get
        assertEquals(material.size,1)
        assertEquals(material.head.hcursor.get[String]("id"),Right("clue_medical_time"))
        assert(!input.user.contains("约19:00是到场"))
        answer("这份意见只写检查时已经死了半小时左右，没有精确的检查时刻。",true)
      }
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_police_medical")
      before <- view(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId("relative"),before.sequence,WitnessId("redding"),"医生判断的死亡时间呢？",Set(ClueId("clue_medical_time"))).run(url,model)
    yield ()).unsafeRunSync()
  }
