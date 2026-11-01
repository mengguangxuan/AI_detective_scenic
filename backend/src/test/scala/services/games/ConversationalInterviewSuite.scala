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
    DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation_v22.json")).plan(c) *>
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *>
      StartGameMessage(owner,id,CaseId("vicarage_study"),Revision(23)).plan(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,route: String)=for
    before <- view(url,id)
    point=before.points.find(_.routes.exists(_.id == RouteId(route))).get.id
    _ <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,point,RouteId(route)).plan)
  yield ()
  private def answer(reply: String,continuous: Boolean)=Json.obj("reply" -> reply.asJson,"disclosureIds" -> Json.arr())
    .deepMerge(if continuous then Json.obj("statementUpdates" -> Json.arr()) else Json.obj()).noSpaces

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
        assert(user.get[Vector[Json]]("history").toOption.get.exists(_.hcursor.get[String]("reply")==Right("你们为什么怀疑安妮？")))
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
      _ = assertEquals(request.get.context.promptVersion,2)
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

  test("legacy frozen contexts keep their JSON shape and their original prompt version") {
    val url=database;val id=SessionId(UUID.randomUUID().toString);val old=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- tx(url)(StartGameMessage(owner,old,CaseId("vicarage_study"),Revision(22)).plan)
      prepared <- tx(url)(AskWitnessMessage(owner,old,RequestId("old"),Sequence(0),WitnessId("redding"),"你承认什么？",Set.empty).plan)
      context=(prepared match
        case InterviewPreparation.Reserved(row) => row.context
        case _ => throw new IllegalStateException("Expected reservation"))
      json=context.asJson
      _ = assert(!json.hcursor.downField("promptVersion").succeeded)
      _ = assertEquals(json.as[InterviewContext].toOption.get,context)
      _ = assertEquals(context.promptVersion,1)
      summary=InterviewRules.rejectionSummary("""{"reply":"PRIVATE_SENTINEL","disclosureIds":["PRIVATE_SENTINEL"],"statementUpdates":[]}""",context)
      _ = assert(!summary.contains("PRIVATE_SENTINEL"))
      _ = assert(summary.contains("\"idsAllowed\":false"))
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
