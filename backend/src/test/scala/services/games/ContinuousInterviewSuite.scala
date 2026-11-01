package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.Paths
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.games.api.*
import services.games.objects.*
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class ContinuousInterviewSuite extends FunSuite:
  private val owner=UserId("continuous-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(for
    _ <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation_v21.json")).plan(c)
    _ <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation_v22.json")).plan(c)
    _ <- StartGameMessage(owner,id,CaseId("vicarage_study"),Revision(22)).plan(c)
  yield ()))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,route: String)=for
    before <- view(url,id)
    point=before.points.find(_.routes.exists(_.id == RouteId(route))).get.id
    result <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,point,RouteId(route)).plan)
  yield result
  private def anne(url: String,id: SessionId)=for
    _ <- read(url,id,"route_redding_account")
    before <- view(url,id)
    _ <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("meet-anne"),before.sequence,OperationId("open_anne_meeting")).plan)
    _ <- read(url,id,"route_anne_statement")
  yield ()
  private def operate(url: String,id: SessionId,operation: String)=for
    before <- view(url,id)
    _ <- tx(url)(ExecuteOperationMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,OperationId(operation)).plan)
  yield ()
  private def ask(url: String,id: SessionId,model: ChatCompletionClient,evidence: Set[ClueId]=Set.empty,request: String="question")=for
    before <- view(url,id)
    result <- AskWitnessMessage(owner,id,RequestId(request),before.sequence,WitnessId("redding"),"她认罪了，你亲眼看见射击了吗？",evidence).run(url,model)
  yield result
  private def output(text: String,ids: Vector[String],updates: Vector[Json]=Vector.empty)=Json.obj(
    "reply" -> text.asJson,"disclosureIds" -> ids.asJson,"statementUpdates" -> updates.asJson).noSpaces
  private def update(text: String,basis: String="clue_anne_statement",answer: String="redding_revised")=Json.obj(
    "answerId" -> answer.asJson,"evidenceIds" -> Vector(basis).asJson,"text" -> text.asJson)
  private def model(raw: String)=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO.pure(raw)

  test("new investigation has one dialogue, no begin operations, and fixed questions save real statement history") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      first <- view(url,id)
      _ = assert(first.followUps.isEmpty)
      _ = assert(!first.points.flatMap(_.operations).exists(_.id.value.startsWith("begin_")))
      _ <- anne(url,id)
      _ <- read(url,id,"route_redding_revised")
      next <- view(url,id)
      _ = assert(next.clues.exists(_.id == ClueId("clue_redding_account")))
      _ = assert(next.clues.exists(_.id == ClueId("clue_redding_revised")))
      _ = assert(!next.points.find(_.id == PointId("point_redding")).get.routes.exists(_.id == RouteId("route_redding_account")))
      turns <- tx(url)(InterviewTurnTable.list(_,id))
      _ = assert(turns.exists(_.statementUpdates.nonEmpty))
      _ = assert(turns.find(_.statementUpdates.nonEmpty).get.shownClues(ClueId("clue_anne_statement")))
      restored <- view(url,id)
      _ = assert(restored.statementComparisons.find(_.witness == WitnessId("redding")).get.rows.head.revised.nonEmpty)
    yield ()).unsafeRunSync()
  }

  test("natural statement change cites actually presented evidence and preserves the exact spoken text for later evidence") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val text="我撤回先前说是我开枪的供述。我进去时他已经死了，地上是我的枪；我拿走枪，以为能替安妮承担。"
    val client=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val c=parser.parse(input.user).toOption.get.hcursor
        assert(c.get[Vector[String]]("statementOptions").toOption.get.contains("redding_revised"))
        assert(c.get[Vector[Json]]("shownEvidence").toOption.get.exists(_.hcursor.get[String]("id") == Right("clue_anne_statement")))
        assert(!c.get[Vector[Json]]("shownEvidence").toOption.get.exists(_.hcursor.get[String]("id") == Right("clue_gun_verification")))
        output(text,Vector("redding_revised"),Vector(update(text)))
      }
    (for
      _ <- setup(url,id)
      _ <- anne(url,id)
      before <- view(url,id)
      command=AskWitnessMessage(owner,id,RequestId("natural"),before.sequence,WitnessId("redding"),"两人都认罪，你到底看见了什么？",Set(ClueId("clue_anne_statement")))
      first <- command.run(url,client)
      retry <- command.run(url,client)
      _ = assertEquals(first,retry)
      next <- view(url,id)
      _ = assertEquals(next.clues.find(_.id == ClueId("clue_redding_revised")).get.text,text)
      _ = assertEquals(next.interviews.count(_.requestId == RequestId("natural")),1)
      _ = assertEquals(next.statementComparisons.find(_.witness == WitnessId("redding")).get.rows.head.revised.get.clue.text,text)
      _ = assert(next.clues.exists(_.id == ClueId("clue_redding_account")))
      _ = assert(next.followUps.isEmpty)
    yield ()).unsafeRunSync()
  }

  test("unshown, forged, unspoken and unregistered updates never grant a revised statement") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val text="我撤回先前独自开枪的说法。"
    val examples=Vector(
      output(text,Vector("redding_revised"),Vector(update(text))),
      output(text,Vector("redding_revised"),Vector(update(text,"clue_future_report"))),
      output("我还没决定重新说什么。",Vector("redding_revised"),Vector(update(text,"clue_redding_account"))),
      output(text,Vector("redding_revised")),
      output(text,Vector("anne_revised"),Vector(update(text,"clue_redding_account","anne_revised"))))
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_redding_account")
      _ <- examples.zipWithIndex.foldLeft(IO.unit) { case (previous,(raw,index)) => previous *> ask(url,id,model(raw),request=s"bad-$index").void }
      saved <- view(url,id)
      _ = assert(saved.interviews.filter(_.requestId.value.startsWith("bad-")).forall(_.mode == InterviewReplyMode.Rejected))
      _ = assert(!saved.clues.exists(_.id == ClueId("clue_redding_revised")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_account")))
    yield ()).unsafeRunSync()
  }

  test("denial and acknowledgement remain dialogue, and earlier actual statements remain in context after twelve turns") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val plain=model(output("我听见了。我仍坚持先前的说法。",Vector.empty))
    val check=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val c=parser.parse(input.user).toOption.get.hcursor
        assertEquals(c.get[Vector[Json]]("history").toOption.get.size,12)
        assert(c.get[Vector[Json]]("recordedStatements").toOption.get.exists(_.hcursor.get[String]("id") == Right("clue_redding_account")))
        output("我先前的供述已经记下了。",Vector.empty)
      }
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_redding_account")
      _ <- (0 until 13).foldLeft(IO.unit)((prev,n) => prev *> ask(url,id,plain,request=s"plain-$n").void)
      _ <- ask(url,id,check,request="memory")
      saved <- view(url,id)
      _ = assert(!saved.clues.exists(_.id == ClueId("clue_redding_revised")))
      _ = assert(saved.interviews.forall(_.mode == InterviewReplyMode.Answered))
    yield ()).unsafeRunSync()
  }

  test("continuous fixed questions reach police comparison and Archer without hidden begin operations or a preparation checklist") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val routes=Vector("route_clock","route_redding_gun","route_redding_clock","route_anne_time","route_anne_gun",
      "route_redding_revised","route_redding_gun_revised","route_redding_scene_revised","route_anne_revised","route_marple_visitors")
    (for
      _ <- setup(url,id)
      _ <- anne(url,id)
      _ <- routes.foldLeft(IO.unit)((prev,route) => prev *> read(url,id,route).void)
      _ <- operate(url,id,"open_valet_meeting")
      _ <- read(url,id,"route_valet_gun")
      _ <- read(url,id,"route_anne_gun_revised")
      _ <- read(url,id,"route_anne_visit_revised")
      _ <- read(url,id,"route_police_revised_review")
      _ <- operate(url,id,"open_archer_meeting")
      _ <- read(url,id,"route_archer_gun_memory")
      _ <- read(url,id,"route_archer_household")
      _ <- read(url,id,"route_police_archer_review")
      saved <- view(url,id)
      _ = assert(saved.followUps.isEmpty)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_archer_gun_memory")))
      _ = assert(saved.statementComparisons.forall(_.rows.forall(row => row.initial.nonEmpty && row.revised.nonEmpty)))
      _ = assert(!saved.journal.exists(_.text.contains("开始补充问询")))
    yield ()).unsafeRunSync()
  }
