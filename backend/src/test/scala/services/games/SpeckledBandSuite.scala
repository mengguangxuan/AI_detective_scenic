package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.games.api.*
import services.games.objects.*
import services.games.tables.SessionRecordTable.SessionRecordTable
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class SpeckledBandSuite extends FunSuite:
  private val owner=UserId("speckled-owner")
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def setup(url: String,id: SessionId)=DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(for
    _ <- ImportCaseMessage(Paths.get("../docs/fixtures/speckled_band_daytime.json")).plan(c)
    _ <- services.TestCases.open(owner,id,CaseId("speckled_band"))(c)
  yield ()))
  private def read(url: String,id: SessionId,route: String): IO[Unit]=for
    before <- view(url,id)
    point=before.points.find(_.routes.exists(_.id == RouteId(route))).get.id
    _ <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,point,RouteId(route)).plan)
  yield ()
  private def move(url: String,id: SessionId,operation: String): IO[Unit]=for
    before <- view(url,id)
    command=ExecuteOperationMessage(owner,id,RequestId(UUID.randomUUID().toString),before.sequence,OperationId(operation))
    receipt <- tx(url)(command.plan)
    repeated <- tx(url)(command.plan)
    _ = assertEquals(repeated,receipt)
  yield ()

  test("independent daytime case opens with authored instances, earns evidence locally, and persists its actual notes without a solved verdict") {
    val path=Files.createTempDirectory("speckled-band-").resolve("db")
    val url=s"jdbc:h2:file:$path;LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      first <- view(url,id)
      _ = assertEquals(first.scene.bundleId,"authored")
      _ = assertEquals(first.scene.render.map(_.room.place),Some(PlaceId("grounds")))
      _ = assertEquals(first.police,None)
      _ = assertEquals(first.witnesses.map(_.id).toSet,Set(WitnessId("helen"),WitnessId("watson")))
      _ = assertEquals(first.clock.map(_.label),Some("第1日 14:00"))
      _ = assert(!first.asJson.noSpaces.contains("snake"))
      guessed <- tx(url)(InvestigateMessage(owner,id,RequestId("guessed"),first.sequence,PointId("sb_point_bed"),RouteId("route_bed")).plan).attempt
      _ = assert(guessed.isLeft)
      _ <- read(url,id,"route_helen_account")
      _ <- read(url,id,"route_will")
      beforeMove <- view(url,id)
      _ <- move(url,id,"travel_grounds_corridor")
      afterMove <- view(url,id)
      _ = assertEquals(afterMove.clock.get.elapsedSeconds-beforeMove.clock.get.elapsedSeconds,300L)
      _ <- move(url,id,"travel_corridor_middle_room")
      room <- view(url,id)
      _ = assertEquals(room.scene.render.get.room.place,PlaceId("middle_room"))
      _ = assert(!room.scene.objects.exists(_.entity == EntityId("safe")))
      _ <- Vector("route_pull","route_vent","route_bed","route_shutters").foldLeft(IO.unit)((io,r) => io *> read(url,id,r))
      _ <- move(url,id,"travel_middle_room_corridor")
      _ <- move(url,id,"travel_corridor_roylott_room")
      _ <- read(url,id,"route_safe_surface")
      _ <- read(url,id,"route_lash")
      safe <- view(url,id)
      _ = assert(!safe.clues.exists(_.id == ClueId("clue_helen_safe_memory")))
      _ = assert(!safe.clues.exists(_.id == ClueId("clue_final_scene")))
      _ <- move(url,id,"travel_roylott_room_corridor")
      _ <- move(url,id,"travel_corridor_grounds")
      _ <- read(url,id,"route_day_notes")
      finished <- view(url,id)
      _ = assert(finished.stage.exists(_.completed))
      _ = assertEquals(finished.reasoning,None)
      _ = assertEquals(finished.conclusion,None)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      _ = assertEquals(restored,finished)
    yield ()).unsafeRunSync()
  }

  test("stage boundary rejects long waits and actions atomically, and cannot reveal or call an absent character") {
    val url=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val id=SessionId(UUID.randomUUID().toString)
    val never=new ChatCompletionClient:
      def complete(input: ChatCompletionInput): IO[String]=IO.raiseError(new AssertionError("Unexpected model call"))
    (for
      _ <- setup(url,id)
      first <- view(url,id)
      absent <- AskWitnessMessage(owner,id,RequestId("absent"),first.sequence,WitnessId("roylott"),"你做过什么？",Set.empty).run(url,never).attempt
      _ = assert(absent.isLeft)
      _ <- (1 to 6).foldLeft(IO.unit)((io,n) => io *> (for
        s <- view(url,id)
        _ <- tx(url)(AdvanceTimeMessage(owner,id,RequestId(s"wait-$n"),s.sequence,30).plan)
      yield ()))
      s <- view(url,id)
      _ <- tx(url)(AdvanceTimeMessage(owner,id,RequestId("to-boundary"),s.sequence,20).plan)
      boundary <- view(url,id)
      _ = assertEquals(boundary.clock.get.maximumWaitMinutes,0)
      tooLate <- tx(url)(AdvanceTimeMessage(owner,id,RequestId("too-late"),boundary.sequence,1).plan).attempt
      _ = assert(tooLate.isLeft)
      lateRead <- tx(url)(InvestigateMessage(owner,id,RequestId("late-read"),boundary.sequence,PointId("sb_point_sb_case_file"),RouteId("route_will")).plan).attempt
      _ = assert(lateRead.isLeft)
      lateQuestion <- AskWitnessMessage(owner,id,RequestId("late-question"),boundary.sequence,WitnessId("helen"),"姐姐说了什么？",Set.empty).run(url,never).attempt
      _ = assert(lateQuestion.isLeft)
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,boundary)
      records <- tx(url)(SessionRecordTable.list(_,id))
      _ = assertEquals(records.size,7)
      _ = assert(!unchanged.clues.exists(_.id == ClueId("clue_final_scene")))
    yield ()).unsafeRunSync()
  }

  test("free interviews freeze only the speaker's own daytime knowledge and actual material presentations") {
    val url=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- Vector("helen","watson").foldLeft(IO.unit) { (io,person) => io *> (for
        before <- view(url,id)
        client=new ChatCompletionClient:
          def complete(input: ChatCompletionInput): IO[String]=IO {
            assert(!input.user.contains("罗伊洛特利用蛇"))
            assert(!input.user.contains("f_snake_route_truth"))
            assert(!input.user.contains("床固定在地板上"))
            if person == "watson" then assert(!input.user.contains("初次可能回避描述"))
            Json.obj("reply" -> Json.fromString("我的记忆只限于当时的经历，不确定的部分我不能肯定。"),
              "disclosureIds" -> Json.arr(),"statementUpdates" -> Json.arr(),
              "focus" -> Json.obj("topic" -> Json.fromString("记忆"),"pendingQuestion" -> Json.Null,"clarification" -> Json.False)).noSpaces
          }
        _ <- AskWitnessMessage(owner,id,RequestId(s"ask-$person"),before.sequence,WitnessId(person),"能说明你的记忆范围吗？",Set.empty).run(url,client)
      yield ()) }
      after <- view(url,id)
      _ = assertEquals(after.interviews.size,2)
      _ = assert(after.interviews.forall(_.mode == InterviewReplyMode.Answered))
      _ = assert(after.clues.isEmpty)
    yield ()).unsafeRunSync()
  }
