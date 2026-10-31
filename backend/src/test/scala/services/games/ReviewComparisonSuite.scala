package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import java.nio.file.Paths
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

class ReviewComparisonSuite extends FunSuite:
  private val owner=UserId("review-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
    ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,route: String): IO[Unit]=for
    before <- view(url,id)
    _ <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,PointId("point_clement"),RouteId(route)).plan)
  yield ()

  test("explicit reveal freezes only acquired materials and never grants author-linked evidence, even when inspection continues") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      first <- setup(url,id)
      _ = assertEquals(first.caseRevision,Revision(20))
      _ = assert(!first.asJson.noSpaces.contains("人物关系与共同动机"))
      _ <- read(url,id,"route_call")
      before <- view(url,id)
      denied <- tx(url)(RevealReasoningMessage(owner,id,RequestId("early"),before.sequence,false).plan).attempt
      _ = assert(denied.isLeft)
      command=RevealReasoningMessage(owner,id,RequestId("reveal"),before.sequence,true)
      receipt <- tx(url)(command.plan)
      ended <- view(url,id)
      frozen=ended.reasoning.get.ending.get
      _ = assertEquals(frozen.mode,ReasoningEndingMode.Revealed)
      _ = assertEquals(frozen.sections.size,6)
      _ = assertEquals(frozen.sections.flatMap(_.materials).map(_.id),Vector(ClueId("clue_call")))
      _ = assert(!frozen.sections.flatMap(_.materials).exists(_.cited))
      _ = assertEquals(ended.clues,before.clues)
      _ = assert(ended.police.get.sharedClues.isEmpty)
      retry <- tx(url)(command.plan)
      _ = assertEquals(retry,receipt)
      _ <- read(url,id,"route_clement_relationship")
      later <- view(url,id)
      _ = assertEquals(later.clues.size,2)
      _ = assertEquals(later.reasoning.get.ending.get,frozen)
      restored <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(restored,later)
    yield ()).unsafeRunSync()
  }

  test("successful ending distinguishes actual last citations from acquired material without regrading or adding a model call") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val judge=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(input.user.contains("reasoning_judge"))
        val ids=io.circe.parser.parse(input.user).toOption.get.hcursor.get[Vector[io.circe.Json]]("criteria").toOption.get
        io.circe.Json.obj("checks" -> io.circe.Json.fromValues(ids.map(c => io.circe.Json.obj("id" -> c.hcursor.get[String]("id").toOption.get.asJson,"satisfied" -> true.asJson))),
          "evidenceIds" -> Vector("clue_call").asJson).noSpaces
      }
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"route_call")
      _ <- read(url,id,"route_clement_relationship")
      before <- view(url,id)
      _ <- EvaluateReasoningMessage(owner,id,RequestId("judge"),before.sequence,Set(PersonId("anne"),PersonId("redding")),Set(ClueId("clue_call")),"协议测试：最后一次提交只引用已经取得的来电材料。").run(url,judge)
      assessed <- view(url,id)
      _ = assert(assessed.reasoning.get.ending.isEmpty)
      _ = assert(!assessed.asJson.noSpaces.contains("证据边界"))
      _ <- tx(url)(RevealReasoningMessage(owner,id,RequestId("close"),assessed.sequence,false).plan)
      ended <- view(url,id)
      materials=ended.reasoning.get.ending.get.sections.flatMap(_.materials)
      _ = assert(materials.find(_.id == ClueId("clue_call")).get.cited)
      _ = assert(!materials.find(_.id == ClueId("clue_clement_relationship")).get.cited)
      _ = assertEquals(ended.reasoning.get.attempts,assessed.reasoning.get.attempts)
      _ = assertEquals(ended.reasoning.get.ending.get.mode,ReasoningEndingMode.Solved)
    yield ()).unsafeRunSync()
  }

  test("legacy endings keep their exact wire shape; published revision 19 retains its original frozen review") {
    val raw="""{"sequence":{"value":3},"mode":"Revealed","review":"original review"}"""
    val ending=io.circe.parser.decode[ReasoningEnding](raw).toOption.get
    assert(ending.sections.isEmpty)
    assertEquals(ending.asJson,io.circe.parser.parse(raw).toOption.get)
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(19),content=LegacyFixture.withoutReview(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield ()))
      before <- view(url,id)
      _ <- tx(url)(RevealReasoningMessage(owner,id,RequestId("old-end"),before.sequence,true).plan)
      ended <- view(url,id)
      old=ended.reasoning.get.ending.get
      _ = assert(old.sections.isEmpty)
      _ = assert(old.review.contains("引用和分数保持不变"))
      restored <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(restored,ended)
      _ = assert(!old.asJson.hcursor.downField("sections").succeeded)
    yield ()).unsafeRunSync()
  }
