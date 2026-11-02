package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.nio.file.{Files,Paths}
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.games.api.*
import services.games.tables.SessionTable.SessionTable
import services.system.api.*
import services.system.objects.*

class OpenMeetingsSuite extends FunSuite:
  private val owner=UserId("open-meetings-owner")
  private val operations=Vector("open_archer_meeting","open_movement_meetings","open_valet_meeting","open_anne_meeting")
  private val invited=Set("anne","valet","stone","hartnell","archer").map(WitnessId.apply)
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def setup(url: String)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *>
    DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation_v24.json")).plan(c) *>
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)))

  test("revision25 changes only contact prerequisites and labels while retaining all testimony and evidence rules") {
    val url=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    (for
      _ <- setup(url)
      old <- tx(url)(CaseRevisionTable.find(_,CaseId("vicarage_study"),Revision(24))).map(_.get)
      current <- tx(url)(CaseRevisionTable.find(_,CaseId("vicarage_study"),Revision(25))).map(_.get)
      _ = assertEquals(current.copy(revision=old.revision,content=current.content.copy(operations=old.content.operations)),old)
      _ = current.content.operations.zip(old.content.operations).foreach { (now,before) =>
        if operations.contains(now.id.value) then
          assertEquals(now.copy(label=before.label,requiredClues=before.requiredClues),before)
          assert(now.requiredClues.isEmpty)
        else assertEquals(now,before)
      }
    yield ()).unsafeRunSync()
  }

  test("all contacts work before obtaining any clue, grant no testimony, retry once and survive file database reopen") {
    val path=Files.createTempDirectory("detective-open-meetings-").resolve("case").toAbsolutePath
    val url=s"jdbc:h2:file:$path"
    val id=SessionId(UUID.randomUUID().toString); val oldId=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url)
      _ <- tx(url)(StartGameMessage(owner,id,CaseId("vicarage_study"),Revision(25)).plan)
      _ <- tx(url)(StartGameMessage(owner,oldId,CaseId("vicarage_study"),Revision(24)).plan)
      before <- view(url,id)
      oldBefore <- view(url,oldId)
      rawBefore <- tx(url)(SessionTable.find(_,id))
      _ = assertEquals(before.clues.size,0)
      _ = assertEquals(before.points.find(_.id == PointId("point_police")).get.operations.map(_.id.value).toSet,operations.toSet)
      _ = assert(!oldBefore.points.find(_.id == PointId("point_police")).get.operations.exists(o => operations.contains(o.id.value)))
      _ <- operations.foldLeft(IO.unit) { (prior,operation) => prior *> (for
        state <- view(url,id)
        command=ExecuteOperationMessage(owner,id,RequestId(operation),state.sequence,OperationId(operation))
        receipt <- tx(url)(command.plan)
        retry <- tx(url)(command.plan)
        _ = assertEquals(retry,receipt)
      yield ()) }
      after <- view(url,id)
      rawAfter <- tx(url)(SessionTable.find(_,id))
      _ = assertEquals(after.clues,before.clues)
      _ = assert(after.interviews.isEmpty)
      _ = assert(invited.subsetOf(after.witnesses.map(_.id).toSet))
      _ = assertEquals(rawAfter.get.facts,rawBefore.get.facts)
      _ = assert(!after.points.flatMap(_.routes).exists(_.id == RouteId("route_anne_sightline_reply")))
      // Each helper closes its connection: these reads reopen the actual H2 file.
      restored <- view(url,id)
      oldAfter <- view(url,oldId)
      _ = assertEquals(restored,after)
      _ = assertEquals(oldAfter,oldBefore)
      _ <- tx(url)(InvestigateMessage(owner,id,RequestId("actual-archer-question"),after.sequence,PointId("point_archer"),RouteId("route_archer_gun_memory")).plan)
      answered <- view(url,id)
      _ = assert(answered.clues.exists(_.id == ClueId("clue_archer_gun_memory")))
    yield ()).unsafeRunSync()
  }

  test("free contact does not bypass the current meeting location") {
    val url=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url)
      _ <- tx(url)(StartGameMessage(owner,id,CaseId("vicarage_study"),Revision(25)).plan)
      _ <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("woods"),Sequence(0),OperationId("enter_woodland")).plan)
      before <- view(url,id)
      denied <- tx(url)(ExecuteOperationMessage(owner,id,RequestId("remote-contact"),before.sequence,OperationId("open_archer_meeting")).plan).attempt
      _ = assert(denied.isLeft)
      after <- view(url,id)
      _ = assertEquals(after,before)
    yield ()).unsafeRunSync()
  }
