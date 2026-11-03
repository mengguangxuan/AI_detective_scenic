package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json,parser}
import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.games.api.*
import services.games.objects.*
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.tables.SessionTable.SessionTable
import services.games.utils.{InterviewRules,SemanticInterviewRules,SemanticEvidence}
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class SemanticInterviewSuite extends FunSuite:
  private val owner=UserId("semantic-owner")
  private def db=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A])=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *>
    DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *>
      StartGameMessage(owner,id,CaseId("vicarage_study")).plan(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,route: String)=for
    before <- view(url,id)
    point=before.points.find(_.routes.exists(_.id == RouteId(route))).get.id
    result <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,point,RouteId(route)).plan)
  yield result
  private def actor(text: String)=Json.obj("reply" -> text.asJson,"focus" -> DialogueFocus(text.take(160),None,false).asJson).noSpaces
  private def recognized(text: String,clue: String,basis: Set[ClueId]=Set.empty)=SpeechRecognition(SpeechResponseKind.Answer,Vector(RecognizedClaim(ClueId(clue),text,basis))).asJson.noSpaces
  private def isReview(input: ChatCompletionInput)=input.system.contains("语义登记核对器")
  private def provider(text: String,clue: String)=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO {
      if isReview(input) then
        assert(!input.user.contains("privateContext"));assert(!input.user.contains("policy"));assert(!input.user.contains("authorTruth"))
        recognized(text,clue)
      else actor(text)
    }

  test("actual speech without registration IDs grants only the independently recognized claim and freezes its own wording") {
    val url=db;val id=SessionId(UUID.randomUUID().toString);val reply="海多克说，检查时已经死亡半小时左右。"
    (for
      _ <- setup(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId("time-only"),Sequence(0),WitnessId("melchett"),"医生怎样估计死亡时间？",Set.empty).run(url,provider(reply,"clue_medical_time"))
      after <- view(url,id)
      _ = assertEquals(after.clues.map(_.id).toSet,Set(ClueId("clue_medical_time")))
      _ = assertEquals(after.clues.head.text,reply)
      _ = assert(after.interviews.last.recognition.exists(_.claims.head.text == reply))
      _ = assert(!after.clues.exists(_.id == ClueId("clue_medical_cause")))
      saved <- tx(url)(InterviewRequestTable.find(_,id,RequestId("time-only")))
      _ = assertEquals(saved.get.context.promptVersion,10)
      _ = assertEquals(saved.get.context.responseVersion,5)
      _ = assertEquals(saved.get.asJson.as[InterviewRequestRecord],Right(saved.get))
    yield ()).unsafeRunSync()
  }

  test("a failed review checkpoints the reply and retry only repeats recognition, with the same receipt") {
    val path=Files.createTempDirectory("semantic-checkpoint-").resolve("case").toAbsolutePath
    val url=s"jdbc:h2:file:$path";val id=SessionId(UUID.randomUUID().toString);val calls=new AtomicInteger()
    val reply="我平时把书房座钟拨快十五分钟。"
    val bad=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO { calls.incrementAndGet(); if isReview(input) then "invalid-json" else actor(reply) }
    val reviewOnly=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO { assert(isReview(input));recognized(reply,"clue_habit") }
    (for
      _ <- setup(url,id)
      command=AskWitnessMessage(owner,id,RequestId("saved-reply"),Sequence(0),WitnessId("clement"),"钟准吗？",Set.empty)
      first <- command.run(url,bad).attempt
      _ = assert(first.isLeft)
      _ = assertEquals(calls.get(),2)
      row <- tx(url)(InterviewRequestTable.find(_,id,RequestId("saved-reply")))
      _ = assertEquals(row.get.draft.flatMap(_.reply),Some(reply))
      _ = assertEquals(row.get.leaseUntilMillis,0L)
      before <- view(url,id)
      _ = assert(before.interviews.isEmpty && before.clues.isEmpty)
      result <- command.run(url,reviewOnly)
      retry <- command.run(url,ChatCompletionClient.unconfigured)
      _ = assertEquals(result,retry)
      after <- view(url,id)
      _ = assertEquals(after.interviews.size,1)
      _ = assertEquals(after.interviews.head.reply,reply)
      _ = assert(after.clues.exists(_.id == ClueId("clue_habit")))
    yield ()).unsafeRunSync()
  }

  test("fixed and free disclosures share grant semantics while preserving their actual different wording") {
    val url=db;val fixed=SessionId(UUID.randomUUID().toString);val free=SessionId(UUID.randomUUID().toString)
    val reply="我把座钟设得快十五分钟，这是我一向的习惯。"
    (for
      _ <- setup(url,fixed)
      _ <- tx(url)(StartGameMessage(owner,free,CaseId("vicarage_study")).plan)
      _ <- read(url,fixed,"route_habit")
      _ <- AskWitnessMessage(owner,free,RequestId("equivalent-question"),Sequence(0),WitnessId("clement"),"你是不是习惯让钟走快一点？",Set.empty).run(url,provider(reply,"clue_habit"))
      a <- view(url,fixed)
      b <- view(url,free)
      _ = assertEquals(a.clues.map(_.id),b.clues.map(_.id))
      sa <- tx(url)(SessionTable.find(_,fixed));sb <- tx(url)(SessionTable.find(_,free))
      _ = assertEquals(sa.get.facts,sb.get.facts)
      _ = assertEquals(b.clues.head.text,reply)
      _ = assert(a.interviews.last.recognition.nonEmpty && b.interviews.last.recognition.nonEmpty)
    yield ()).unsafeRunSync()
  }

  test("received material is independent of agreement and remains known through fixed and free entry paths") {
    val url=db;val id=SessionId(UUID.randomUUID().toString)
    val reply="我看见座钟记录了，但我现在不想解释它。"
    val refusing=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO.pure(if isReview(input) then SpeechRecognition(SpeechResponseKind.Refusal,Vector.empty).asJson.noSpaces else actor(reply))
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_clock")
      before <- view(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId("refusal"),before.sequence,WitnessId("redding"),"请看这份座钟记录。",Set(ClueId("clue_clock"))).run(url,refusing)
      after <- view(url,id)
      _ = assertEquals(after.clues,before.clues)
      next <- tx(url)(AskWitnessMessage(owner,id,RequestId("receipt-persists"),after.sequence,WitnessId("redding"),"刚才给你的记录呢？",Set.empty).plan)
      row=next match
        case InterviewPreparation.Reserved(r) => r
        case _ => fail("Expected request")
      _ = assert(row.context.evidence.exists(_.id == ClueId("clue_clock")))
      _ = assert(!row.context.evidence.exists(_.id == ClueId("clue_anne_statement")))
      content <- tx(url)(CaseTable.find(_,CaseId("vicarage_study"))).map(_.get.content)
      state <- tx(url)(SessionTable.find(_,id)).map(_.get)
      turns <- tx(url)(InterviewTurnTable.list(_,id))
      other=InterviewRules.context(content,state,WitnessId("mary"),"看过这份记录吗？",Set.empty,turns,Vector.empty).toOption.get
      _ = assert(!other.evidence.exists(_.id == ClueId("clue_clock")))
    yield ()).unsafeRunSync()
  }

  test("semantic quote, authorization, prior statement and basis checks cannot be bypassed by a registration tag") {
    val url=db;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      prepared <- tx(url)(AskWitnessMessage(owner,id,RequestId("checks"),Sequence(0),WitnessId("redding"),"你要改口吗？",Set.empty).plan)
      row=prepared match
        case InterviewPreparation.Reserved(r) => r
        case _ => fail("Expected request")
      draft=InterviewRules.decision(actor("我没说要改变原供。"),row.context).get
      legacyTags=parser.parse(actor(draft.reply.get)).toOption.get.deepMerge(Json.obj("disclosureIds" -> Vector("future_confession").asJson,"statementUpdates" -> Json.arr()))
      ignored=InterviewRules.decision(legacyTags.noSpaces,row.context).get
      _ = assert(ignored.answerIds.isEmpty && ignored.statementUpdates.isEmpty && ignored.recognition.isEmpty)
      _ = assert(!InterviewRules.validDecision(ignored,row.context))
      _ = assert(SemanticInterviewRules.review(recognized("没有说过的句子","clue_redding_account"),draft,row.context).exists(d => d.recognition.exists(r => r.claims.isEmpty && r.unconfirmed)))
      _ = assert(SemanticInterviewRules.review(recognized(draft.reply.get,"clue_anne_statement"),draft,row.context).exists(d => d.recognition.exists(r => r.claims.isEmpty && r.unconfirmed)))
      _ = assert(SemanticInterviewRules.review(recognized(draft.reply.get,"clue_redding_revised"),draft,row.context).exists(d => d.recognition.exists(r => r.claims.isEmpty && r.unconfirmed)))
      _ = assert(SemanticInterviewRules.review(SpeechRecognition(SpeechResponseKind.Clarification,Vector(RecognizedClaim(ClueId("clue_redding_account"),draft.reply.get,Set.empty))).asJson.noSpaces,draft,row.context).exists(d => d.recognition.exists(r => r.claims.isEmpty && r.unconfirmed)))
      raw=SpeechRecognition(SpeechResponseKind.Answer,Vector(RecognizedClaim(ClueId("clue_redding_account"),draft.reply.get,Set(ClueId("unseen"))))).asJson.noSpaces
      _ = assert(SemanticInterviewRules.review(raw,draft,row.context).exists(d => d.recognition.exists(r => r.claims.isEmpty && r.unconfirmed)))
      _ = assert(SemanticInterviewRules.review(SpeechRecognition(SpeechResponseKind.Maintaining,Vector.empty).asJson.noSpaces,draft,row.context).nonEmpty)
      mixed=SpeechRecognition(SpeechResponseKind.Answer,Vector(RecognizedClaim(ClueId("clue_redding_account"),draft.reply.get,Set.empty),RecognizedClaim(ClueId("unknown"),draft.reply.get,Set.empty)))
      partial=SemanticInterviewRules.review(mixed.asJson.noSpaces,draft,row.context).get
      _ = assertEquals(partial.recognition.get.claims.map(_.clue),Vector(ClueId("clue_redding_account")))
      _ = assert(partial.recognition.get.unconfirmed)
      _ = assert(InterviewRules.validDecision(partial,row.context))
      extra=SpeechRecognition(SpeechResponseKind.Answer,Vector(RecognizedClaim(ClueId("clue_redding_account"),draft.reply.get,Set.empty))).asJson.deepMerge(Json.obj("explanation" -> "PRIVATE_AUXILIARY_SENTINEL".asJson))
      accepted=SemanticInterviewRules.review(extra.noSpaces,draft,row.context).get
      _ = assertEquals(accepted.recognition.get.claims.size,1)
      _ = assert(!accepted.asJson.noSpaces.contains("PRIVATE_AUXILIARY_SENTINEL"))
      unknown=SemanticInterviewRules.review(extra.deepMerge(Json.obj("kind" -> "unknown_label".asJson)).noSpaces,draft,row.context).get
      _ = assert(unknown.recognition.get.claims.isEmpty && unknown.recognition.get.unconfirmed)


    yield ()).unsafeRunSync()
  }

  test("author-declared equivalent propositions satisfy the same fixed presentation without inventing a canonical material receipt") {
    val url=db;val initial=SessionId(UUID.randomUUID().toString);val id=SessionId(UUID.randomUUID().toString)
    val aliasId=ClueId("clue_clock_equivalent")
    (for
      _ <- setup(url,initial)
      original <- tx(url)(CaseTable.find(_,CaseId("vicarage_study"))).map(_.get)
      clock=original.content.clues.find(_.id == ClueId("clue_clock")).get
      content=original.content.copy(clues=original.content.clues :+ clock.copy(id=aliasId,title=Text("同一座钟读数的另一份记录")),
        routes=original.content.routes.map(r => if r.id == RouteId("route_clock") then r.copy(clues=Vector(aliasId)) else r))
      alternate=original.copy(caseId=CaseId("equivalent_material_case"),content=content)
      _ <- tx(url)(CaseTable.insert(_,alternate))
      _ <- tx(url)(RegisterPlayableCaseMessage(CaseReleaseRecord(alternate.caseId,"同义材料协议测试",Vector.empty,"study")).plan)
      _ <- tx(url)(StartGameMessage(owner,id,alternate.caseId).plan)
      _ <- read(url,id,"route_clock")
      _ <- read(url,id,"route_habit")
      _ <- read(url,id,"route_clock_presented")
      before <- view(url,id)
      _ = assert(before.clues.exists(_.id == aliasId))
      _ = assert(!before.clues.exists(_.id == clock.id))
      prepared <- tx(url)(AskWitnessMessage(owner,id,RequestId("same-meaning"),before.sequence,WitnessId("clement"),"刚才那份读数呢？",Set.empty).plan)
      row=prepared match
        case InterviewPreparation.Reserved(r) => r
        case _ => fail("Expected request")
      _ = assert(row.context.evidence.exists(_.id == aliasId))
      _ = assert(!row.context.evidence.exists(_.id == clock.id))
      _ = assertEquals(SemanticEvidence.resolve(content,Set(clock.id),Set(aliasId)),Some(Set(aliasId)))
    yield ()).unsafeRunSync()
  }

  test("saved replies are fenced against an expired worker, another user and altered draft content") {
    val url=db;val id=SessionId(UUID.randomUUID().toString)
    def reserved(value: InterviewPreparation)=value match
      case InterviewPreparation.Reserved(r) => r
      case _ => fail("Expected reservation")
    (for
      _ <- setup(url,id)
      command=AskWitnessMessage(owner,id,RequestId("fenced-draft"),Sequence(0),WitnessId("clement"),"钟准吗？",Set.empty)
      prepared <- tx(url)(command.plan)
      old=reserved(prepared)
      _ <- tx(url)(InterviewRequestTable.replace(_,old.copy(leaseUntilMillis=0L),old.leaseToken))
      renewed <- tx(url)(command.plan)
      current=reserved(renewed)
      draft=InterviewRules.decision(actor("我把钟拨快十五分钟。"),current.context).get
      expired <- tx(url)(SaveInterviewDraftMessage(owner,id,current.requestId,old.leaseToken,draft,false).plan).attempt
      _ = assert(expired.isLeft)
      foreign <- tx(url)(SaveInterviewDraftMessage(UserId("another-user"),id,current.requestId,current.leaseToken,draft,false).plan).attempt
      _ = assert(foreign.isLeft)
      _ <- tx(url)(SaveInterviewDraftMessage(owner,id,current.requestId,current.leaseToken,draft,false).plan)
      altered <- tx(url)(SaveInterviewDraftMessage(owner,id,current.requestId,current.leaseToken,draft.copy(reply=Some("改掉原话。"),focus=Some(DialogueFocus("改掉原话。",None,false))),false).plan).attempt
      _ = assert(altered.isLeft)
      saved <- tx(url)(InterviewRequestTable.find(_,id,current.requestId))
      _ = assertEquals(saved.get.draft,Some(draft))
      _ = assertEquals(saved.get.context,old.context)
    yield ()).unsafeRunSync()
  }
