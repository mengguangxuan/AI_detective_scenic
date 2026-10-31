package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.nio.file.Paths
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.api.*
import services.system.api.*
import services.system.objects.*

class PlanterSuite extends FunSuite:
  private val owner=UserId("planter-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId)=DatabaseSession.connection(url).use(c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def operation(id: SessionId,seq: Sequence,op: String)=
    ExecuteOperationMessage(owner,id,RequestId(UUID.randomUUID().toString),seq,OperationId(op))
  private def read(id: SessionId,seq: Sequence)=
    InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),seq,PointId("point_planter_interior"),RouteId("route_planter_interior"))

  test("lifting exposes an inspection, never a clue; hidden interior cannot be investigated by guessing its ID") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      first <- setup(url,id)
      _ = assertEquals(first.caseRevision,Revision(20))
      _ = assertEquals(first.scene.revision,"5")
      _ = assert(!first.scene.objects.exists(_.node == SceneNodeId("node_planter_interior")))
      denied <- tx(url)(read(id,first.sequence).plan).attempt
      _ = assert(denied.isLeft)
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,first)
      _ <- tx(url)(operation(id,first.sequence,"lift_planter").plan)
      lifted <- view(url,id)
      _ = assert(lifted.scene.objects.exists(_.node == SceneNodeId("node_planter_interior")))
      _ = assert(lifted.clues.isEmpty)
      command=read(id,lifted.sequence)
      receipt <- tx(url)(command.plan)
      inspected <- view(url,id)
      _ = assertEquals(inspected.clues.map(_.id),Vector(ClueId("clue_planter")))
      retry <- tx(url)(command.plan)
      _ = assertEquals(retry,receipt)
      _ = assertEquals(inspected.journal.size,2)
      _ <- tx(url)(operation(id,inspected.sequence,"replace_planter").plan)
      closed <- view(url,id)
      _ = assert(!closed.scene.objects.exists(_.node == SceneNodeId("node_planter_interior")))
      _ = assertEquals(closed.clues,inspected.clues)
      restored <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(restored,closed)
    yield ()).unsafeRunSync()
  }

  test("operation rollback, original receipt retry and repeat inspections preserve authoritative progress") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      first <- setup(url,id)
      command=operation(id,first.sequence,"lift_planter")
      rolledBack <- tx(url)(c => command.plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(rolledBack.isLeft)
      afterRollback <- view(url,id)
      _ = assertEquals(afterRollback,first)
      receipt <- tx(url)(command.plan)
      retry <- tx(url)(command.plan)
      _ = assertEquals(receipt,retry)
      opened <- view(url,id)
      _ = assertEquals(opened.sequence,Sequence(1))
      _ <- tx(url)(read(id,opened.sequence).plan)
      inspected <- view(url,id)
      _ <- tx(url)(operation(id,inspected.sequence,"replace_planter").plan)
      closed <- view(url,id)
      denied <- tx(url)(read(id,closed.sequence).plan).attempt
      _ = assert(denied.isLeft)
      _ <- tx(url)(operation(id,closed.sequence,"lift_planter").plan)
      reopened <- view(url,id)
      _ = assertEquals(reopened.clues,inspected.clues)
      _ = assert(reopened.points.find(_.id == PointId("point_planter_interior")).get.routes.isEmpty)
    yield ()).unsafeRunSync()
  }

  test("revision 17 retains its original scene and rejects newly published planter operations") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        previous=latest.copy(revision=Revision(17),content=LegacyFixture.withoutPlanter(latest.content))
        _ <- CaseRevisionTable.insert(c,previous)
        _ <- StartGameMessage(owner,id,previous.caseId,previous.revision).plan(c)
      yield ()))
      before <- view(url,id)
      _ = assertEquals(before.scene.revision,"4")
      _ = assertEquals(before.scene.objects.size,8)
      _ = assert(before.reasoning.nonEmpty)
      denied <- tx(url)(operation(id,before.sequence,"lift_planter").plan).attempt
      _ = assert(denied.isLeft)
      restored <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(restored,before)
    yield ()).unsafeRunSync()
  }
