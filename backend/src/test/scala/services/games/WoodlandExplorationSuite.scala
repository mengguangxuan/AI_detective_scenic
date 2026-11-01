package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import java.nio.file.Paths
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.games.api.*
import services.games.objects.apiTypes.InvestigationSnapshot
import services.system.api.*
import services.system.objects.*

class WoodlandExplorationSuite extends FunSuite:
  private val owner=UserId("woodland-exploration-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId,revision: Int=24)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *>
    DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation_v23.json")).plan(c) *>
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *>
      StartGameMessage(owner,id,CaseId("vicarage_study"),Revision(revision)).plan(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def move(url: String,id: SessionId,op: String)=for
    before <- view(url,id)
    result <- tx(url)(ExecuteOperationMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,OperationId(op)).plan)
  yield result

  test("entering uses an authored stop, retains prior interviews and never grants distant or hidden evidence") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      before <- view(url,id)
      _ <- tx(url)(InvestigateMessage(owner,id,RequestId("first-account"),before.sequence,PointId("point_redding"),RouteId("route_redding_account")).plan)
      first <- view(url,id)
      _ = assert(first.scene.travel.exists(_.operation == OperationId("enter_woodland")))
      distant <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("far-brush"),first.sequence,OperationId("brush_woodland_leaves")).plan).attempt
      _ = assert(distant.isLeft)
      entry=ExecuteOperationMessage(owner,id,RequestId("enter"),first.sequence,OperationId("enter_woodland"))
      result <- tx(url)(entry.plan)
      retry <- tx(url)(entry.plan)
      _ = assertEquals(result,retry)
      woods <- view(url,id)
      _ = assertEquals(woods.scene.bundleId,"woodland")
      _ = assertEquals(woods.scene.playerPosition,Some(Vec3(0,0,5)))
      _ = assertEquals(woods.clues,first.clues)
      _ = assertEquals(woods.interviews,first.interviews)
      _ = assert(woods.interviews.forall(t => woods.witnesses.exists(_.id == t.witness)))
      _ = assert(woods.scene.objects.forall(_.node != SceneNodeId("node_woodland_fragment")))
      collect <- tx(url)(InvestigateMessage(owner,id,RequestId("far-collect"),woods.sequence,PointId("point_woodland_fragment"),RouteId("route_collect_woodland_sample")).plan).attempt
      _ = assert(collect.isLeft)
      skip <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("skip-stop"),woods.sequence,OperationId("woodland_to_log")).plan).attempt
      _ = assert(skip.isLeft)
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,woods)
    yield ()).unsafeRunSync()
  }

  test("walking close, uncovering and actually collecting are separate saved actions; returning enables professional identification") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- move(url,id,"enter_woodland")
      _ <- move(url,id,"woodland_to_clearing")
      clearing <- view(url,id)
      _ = assert(!clearing.points.exists(_.id == PointId("point_woodland_leaves")))
      _ <- move(url,id,"woodland_to_log")
      _ <- move(url,id,"brush_woodland_leaves")
      revealed <- view(url,id)
      _ = assert(revealed.scene.objects.exists(_.node == SceneNodeId("node_woodland_fragment")))
      _ = assert(!revealed.clues.exists(_.id == ClueId("clue_woodland_sample")))
      _ = assertEquals(revealed.scene.playerPosition,Some(Vec3(2.8,0,-4.5)))
      collect=InvestigateMessage(owner,id,RequestId("collect"),revealed.sequence,PointId("point_woodland_fragment"),RouteId("route_collect_woodland_sample"))
      first <- tx(url)(collect.plan)
      saved <- view(url,id)
      _ = assertEquals(saved.asJson.as[InvestigationSnapshot],Right(saved))
      _ = assert(saved.clues.exists(c => c.id == ClueId("clue_woodland_sample") && c.text.contains("现场采集")))
      _ <- move(url,id,"return_study_log")
      retry <- tx(url)(collect.plan)
      _ = assertEquals(first,retry)
      home <- view(url,id)
      _ = assertEquals(home.scene.bundleId,"study")
      _ = assert(home.police.nonEmpty)
      _ = assert(!home.externalChecks.exists(_.id == ExternalCheckId("woodland_search")))
      _ <- tx(url)(RequestExternalCheckMessage(owner,id,RequestId("identify"),home.sequence,ExternalCheckId("crystal_opinion")).plan)
      waiting <- view(url,id)
      _ <- tx(url)(AdvanceTimeMessage(owner,id,RequestId("wait"),waiting.sequence,10).plan)
      done <- view(url,id)
      _ = assert(done.clues.exists(c => c.id == ClueId("clue_crystal_opinion") && c.text.contains("并非化验确认")))
      _ <- move(url,id,"enter_woodland")
      _ <- move(url,id,"woodland_to_clearing")
      _ <- move(url,id,"woodland_to_log")
      returned <- view(url,id)
      duplicate <- tx(url)(InvestigateMessage(owner,id,RequestId("collect-again"),returned.sequence,PointId("point_woodland_fragment"),RouteId("route_collect_woodland_sample")).plan).attempt
      _ = assert(duplicate.isLeft)
      _ = assertEquals(returned.clues.count(_.id == ClueId("clue_woodland_sample")),1)
    yield ()).unsafeRunSync()
  }

  test("revision23 retains the police search and has no newly invented movement or scene fields") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id,23)
      before <- view(url,id)
      _ = assert(before.scene.travel.isEmpty)
      _ = assert(before.scene.playerPosition.isEmpty)
      _ = assert(!before.scene.asJson.hcursor.downField("travel").succeeded)
      _ = assert(before.externalChecks.exists(_.id == ExternalCheckId("woodland_search")))
      denied <- move(url,id,"enter_woodland").attempt
      _ = assert(denied.isLeft)
      after <- view(url,id)
      _ = assertEquals(before,after)
    yield ()).unsafeRunSync()
  }
