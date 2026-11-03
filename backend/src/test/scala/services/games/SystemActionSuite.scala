package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Paths}
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.cases.objects.content.CasePersonBrief
import services.cases.objects.investigation.*
import services.cases.tables.CaseTable.CaseTable
import services.games.api.*
import services.games.objects.*
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

// A capability fixture, not an additional independent story or a Speckled Band acceptance substitute.
class SystemActionSuite extends FunSuite:
  private val owner=UserId("owner")
  private val suspect=PersonId("test_person")
  private val evidence=Set(ClueId("clue_letter"))
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def setup(url: String,id: SessionId,semantic: Boolean): IO[Unit]=DatabaseSession.connection(url).use { c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(for
      base <- ImportCaseMessage(Paths.get("../docs/fixtures/minimal_investigation.json")).plan(c)
      evaluation=Option.when(semantic)(ReasoningEvaluationSpec("固定审核真相",Vector(ReasoningCriterion("basis","材料依据","解释取得的材料"))))
      content=base.content.copy(police=None,witnesses=Vector.empty,
        publicPersons=Vector(CasePersonBrief(suspect,"测试人物","相关人","认识","涉及材料",None)),
        clock=Some(InvestigationClock(540,60)),
        conclusion=Some(CaseConclusionSpec(Set(suspect),Set(suspect),Vector(ConclusionEvidenceGroup("材料",evidence)),"固定作者复盘",evaluation)))
      record=base.copy(caseId=CaseId("system_action_fixture"),content=content)
      _ <- CaseTable.insert(c,record)
      _ <- StartGameMessage(owner,id,record.caseId).plan(c)
      _ <- ExecuteOperationMessage(owner,id,RequestId("open"),Sequence(0),OperationId("open_drawer")).plan(c)
      _ <- InvestigateMessage(owner,id,RequestId("read"),Sequence(1),PointId("point_letter"),RouteId("route_letter")).plan(c)
    yield ())
  }
  private val judge=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO.pure(Json.obj(
      "checks" -> Vector(Json.obj("id" -> "basis".asJson,"satisfied" -> true.asJson)).asJson,
      "evidenceIds" -> Vector("clue_letter").asJson).noSpaces)

  test("without police, waiting, semantic assessment and solved ending persist and retry without a scene point") {
    val directory=Files.createTempDirectory("system-actions-")
    val url=s"jdbc:h2:file:${directory.resolve("db")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id,true)
      before <- view(url,id)
      _ = assertEquals(before.police,None)
      wait=AdvanceTimeMessage(owner,id,RequestId("wait"),before.sequence,5)
      receipt <- tx(url)(wait.plan)
      again <- tx(url)(wait.plan)
      _ = assertEquals(again,receipt)
      invalid <- tx(url)(AdvanceTimeMessage(owner,id,RequestId("invalid"),receipt.sequence,31).plan).attempt
      _ = assert(invalid.isLeft)
      denied <- tx(url)(AdvanceTimeMessage(UserId("foreign"),id,wait.requestId,before.sequence,5).plan).attempt
      _ = assert(denied.isLeft)
      assessment=EvaluateReasoningMessage(owner,id,RequestId("assess"),receipt.sequence,Set(suspect),evidence,"材料与责任之间的推理解释文字。")
      assessed <- assessment.run(url,judge)
      repeated <- assessment.run(url,judge)
      _ = assertEquals(repeated,assessed)
      ending=RevealReasoningMessage(owner,id,RequestId("ending"),assessed.sequence,false)
      ended <- tx(url)(ending.plan)
      retry <- tx(url)(ending.plan)
      _ = assertEquals(retry,ended)
      saved <- view(url,id)
      _ = assertEquals(saved.reasoning.flatMap(_.ending).map(_.mode),Some(ReasoningEndingMode.Solved))
      records <- tx(url)(SessionRecordTable.list(_,id))
      _ = assertEquals(records.takeRight(3).flatMap(_.systemAction),Vector(InvestigationSystemAction.Wait,InvestigationSystemAction.EvaluateReasoning,InvestigationSystemAction.RevealReasoning))
      _ = assert(records.takeRight(3).forall(r => r.point.isEmpty && r.route.isEmpty && r.operation.isEmpty))
      _ = assert(records.take(2).forall(r => r.point.nonEmpty && r.systemAction.isEmpty))
      // Every transaction has already closed its file connection; initialize again without losing records or receipts.
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
      restoredReceipt <- tx(url)(ending.plan)
      _ = assertEquals(restoredReceipt,ended)
    yield ()).unsafeRunSync()
  }

  test("coverage submission and voluntary reveal also work without police, while unearned materials remain forbidden") {
    val url=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id,false)
      before <- view(url,id)
      bad <- tx(url)(SubmitConclusionMessage(owner,id,RequestId("bad"),before.sequence,Set(suspect),Set(ClueId("unknown")),"不应允许引用未取得的材料。").plan).attempt
      _ = assert(bad.isLeft)
      submit=SubmitConclusionMessage(owner,id,RequestId("submit"),before.sequence,Set(suspect),evidence,"对已取得材料进行完整的推理说明。")
      result <- tx(url)(submit.plan)
      repeated <- tx(url)(submit.plan)
      _ = assertEquals(repeated,result)
      records <- tx(url)(SessionRecordTable.list(_,id))
      _ = assertEquals(records.last.systemAction,Some(InvestigationSystemAction.SubmitConclusion))
      _ = assertEquals(records.last.point,None)
      other=SessionId(UUID.randomUUID().toString)
      _ <- tx(url)(StartGameMessage(owner,other,CaseId("system_action_fixture")).plan)
    yield ()).unsafeRunSync()
    val semanticUrl=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val semanticId=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(semanticUrl,semanticId,true)
      before <- view(semanticUrl,semanticId)
      _ <- tx(semanticUrl)(RevealReasoningMessage(owner,semanticId,RequestId("abandon"),before.sequence,true).plan)
      after <- view(semanticUrl,semanticId)
      _ = assertEquals(after.reasoning.flatMap(_.ending).map(_.mode),Some(ReasoningEndingMode.Revealed))
    yield ()).unsafeRunSync()
  }

  test("record schema evolution preserves point records and converts only exact prior system records; invalid contexts cannot persist") {
    val url=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id,true)
      original <- tx(url)(SessionRecordTable.list(_,id))
      _ <- DatabaseSession.connection(url).use(c => IO.blocking {
        val s=c.createStatement()
        try
          s.execute("ALTER TABLE session_records DROP CONSTRAINT record_context_check")
          s.execute("ALTER TABLE session_records DROP COLUMN system_action")
          s.execute("ALTER TABLE session_records ALTER COLUMN point_id SET NOT NULL")
          s.execute("ALTER TABLE session_records ADD CONSTRAINT prior_route_check CHECK ((route_id IS NULL AND operation_id IS NOT NULL) OR (route_id IS NOT NULL AND operation_id IS NULL))")
          s.execute("INSERT INTO session_records SELECT 'prior:wait',session_id,'prior',3,story_time_seconds,point_id,'advance_time',NULL,'wait','[]','[]' FROM session_records WHERE record_id='read:investigation'")
        finally s.close()
      })
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      converted <- tx(url)(SessionRecordTable.list(_,id))
      _ = assertEquals(converted.take(2),original)
      _ = assertEquals(converted.last.systemAction,Some(InvestigationSystemAction.Wait))
      _ = assert(converted.last.point.isEmpty && converted.last.route.isEmpty && converted.last.operation.isEmpty)
      invalid=converted.last.copy(id=RecordId("invalid"),requestId=RequestId("invalid"),sequence=Sequence(4),point=Some(PointId("point_drawer")))
      rejected <- tx(url)(SessionRecordTable.insert(_,invalid)).attempt
      _ = assert(rejected.isLeft)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      repeated <- tx(url)(SessionRecordTable.list(_,id))
      _ = assertEquals(repeated,converted)
    yield ()).unsafeRunSync()
  }
