package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser
import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.games.api.*
import services.games.objects.*
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class IndependentChecksSuite extends FunSuite:
  private val owner=UserId("checks-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(run: java.sql.Connection => IO[A]): IO[A] =
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(run(c)))
  private def setup(url: String,id: SessionId): IO[Unit] = DatabaseSession.connection(url).use { c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)).void
  }
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,point: String,route: String): IO[Unit] = for
    state <- view(url,id)
    _ <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),state.sequence,PointId(point),RouteId(route)).plan)
  yield ()
  private def execute(url: String,id: SessionId,operation: String): IO[Unit] = for
    state <- view(url,id)
    _ <- tx(url)(ExecuteOperationMessage(owner,id,RequestId(UUID.randomUUID().toString),state.sequence,OperationId(operation)).plan)
  yield ()
  private def anne(url: String,id: SessionId): IO[Unit] =
    read(url,id,"point_redding","route_redding_account") *> execute(url,id,"open_anne_meeting")
  private def statements(url: String,id: SessionId): IO[Unit] =
    anne(url,id) *> read(url,id,"point_anne","route_anne_statement") *> read(url,id,"point_anne","route_anne_gun")
  private def ask(url: String,id: SessionId,witness: String,model: ChatCompletionClient,evidence: Set[ClueId]=Set.empty): IO[Unit] = for
    state <- view(url,id)
    _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),state.sequence,WitnessId(witness),"请说明你记得的经过。",evidence).run(url,model)
  yield ()
  private def response(id: String)=s"""{"reply":"这是我记得的经过。","disclosureIds":["$id"]}"""
  private def client(raw: String)=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO.pure(raw)
  private def code(value: Either[Throwable,?])=value.left.toOption.collect { case e: GameError => e.code }

  test("hidden checks cannot be guessed or requested from the model; arranging does not disclose testimony") {
    val url=database;val id=SessionId(UUID.randomUUID().toString);val calls=new AtomicInteger()
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO { calls.incrementAndGet();response("valet_gun") }
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assertEquals(initial.caseRevision,Revision(13))
      _ = assert(initial.people.filter(p => Set("valet","stone","hartnell")(p.id.value)).forall(_.witness.isEmpty))
      early <- execute(url,id,"open_movement_meetings").attempt
      hidden <- read(url,id,"point_valet","route_valet_gun").attempt
      question <- ask(url,id,"valet",model).attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ = assertEquals(code(hidden),Some(ErrorCode.AccessDenied))
      _ = assertEquals(code(question),Some(ErrorCode.AccessDenied))
      _ = assertEquals(calls.get(),0)
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,initial)
      _ <- anne(url,id)
      premature <- execute(url,id,"open_valet_meeting").attempt
      _ = assertEquals(code(premature),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"point_anne","route_anne_gun")
      _ <- execute(url,id,"open_valet_meeting")
      arranged <- view(url,id)
      _ = assert(arranged.witnesses.exists(_.id == WitnessId("valet")))
      _ = assert(!arranged.clues.exists(_.id == ClueId("clue_valet_gun")))
      _ = assert(!arranged.witnesses.exists(_.id == WitnessId("stone")))
    yield ()).unsafeRunSync()
  }

  test("both meetings and actually collected independent statements persist; a lost operation reply retries once") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("checks-stage-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- statements(url,id)
      before <- view(url,id)
      command=ExecuteOperationMessage(owner,id,RequestId("arrange-movements"),before.sequence,OperationId("open_movement_meetings"))
      result <- tx(url)(command.plan)
      retry <- tx(url)(command.plan)
      _ = assertEquals(result,retry)
      arranged <- view(url,id)
      _ = assertEquals(arranged.sequence.value,before.sequence.value+1)
      _ = assertEquals(arranged.clues,before.clues)
      _ <- execute(url,id,"open_valet_meeting")
      _ <- read(url,id,"point_valet","route_valet_gun")
      _ <- read(url,id,"point_stone","route_stone_walk")
      _ <- read(url,id,"point_stone","route_stone_inn")
      _ <- read(url,id,"point_hartnell","route_hartnell_visit")
      saved <- view(url,id)
      _ = assert(saved.clues.find(_.id == ClueId("clue_valet_gun")).get.text.contains("没有进行抽屉搜查"))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_account")))
      _ = assert(!saved.clues.exists(_.id == ClueId("clue_redding_revised")))
      _ = assertEquals(saved.stage.get.completed,false)
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      retriedAfterReopen <- tx(url)(command.plan)
      _ = assertEquals(restored,saved)
      _ = assertEquals(retriedAfterReopen,result)
    yield ()).unsafeRunSync()
  }

  test("police comparison needs actual acquired and presented evidence, and remembers earlier presentation") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val model=client(response("police_gun_check"))
    val evidence=Set("clue_anne_gun","clue_valet_gun").map(ClueId.apply)
    (for
      _ <- setup(url,id)
      forged <- ask(url,id,"melchett",model,evidence).attempt
      _ = assertEquals(code(forged),Some(ErrorCode.AccessDenied))
      _ <- statements(url,id)
      _ <- execute(url,id,"open_valet_meeting")
      _ <- read(url,id,"point_valet","route_valet_gun")
      _ <- ask(url,id,"melchett",model)
      owned <- view(url,id)
      _ = assertEquals(owned.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assert(!owned.clues.exists(_.id == ClueId("clue_police_gun_check")))
      _ <- ask(url,id,"melchett",model,evidence)
      _ <- ask(url,id,"melchett",model)
      after <- view(url,id)
      _ = assertEquals(after.interviews.takeRight(2).map(_.mode),Vector(InterviewReplyMode.Answered,InterviewReplyMode.Answered))
      _ = assert(after.clues.exists(_.id == ClueId("clue_police_gun_check")))
      _ = assertEquals(after.police.get.sharedClues,evidence)
      _ = assert(!after.asJson.noSpaces.contains("privateContext"))
    yield ()).unsafeRunSync()
  }

  test("new witnesses receive their own scope and cannot disclose another person's testimony") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val json=parser.parse(input.user).toOption.get.hcursor
        assertEquals(json.downField("witness").get[String]("name"),Right("斯通博士"))
        assert(json.get[Vector[String]]("knowledge").toOption.get.exists(_.contains("六点四十")))
        assert(!input.user.contains("valet_gun"))
        assert(!input.user.contains("你知道自己参与与雷丁的共谋"))
        response("stone_inn")
      }
    (for
      _ <- setup(url,id)
      _ <- statements(url,id)
      _ <- execute(url,id,"open_movement_meetings")
      _ <- ask(url,id,"stone",model)
      _ <- ask(url,id,"hartnell",client(response("stone_inn")))
      saved <- view(url,id)
      _ = assertEquals(saved.interviews.map(_.mode),Vector(InterviewReplyMode.Answered,InterviewReplyMode.Rejected))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_stone_inn")))
      _ = assert(!saved.clues.exists(_.id == ClueId("clue_hartnell_visit")))
    yield ()).unsafeRunSync()
  }

  test("sound comparison has a deterministic fallback and preserves approximate source attribution") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      early <- read(url,id,"point_police","route_police_sound_check").attempt
      _ = assertEquals(code(early),Some(ErrorCode.ConditionFailed))
      _ <- read(url,id,"point_mary","route_mary_sound")
      _ <- read(url,id,"point_mary","route_mary_fish_time")
      _ <- read(url,id,"point_marple","route_marple_sound")
      _ <- read(url,id,"point_police","route_police_sound_check")
      after <- view(url,id)
      _ = assert(after.clues.exists(_.id == ClueId("clue_police_sound_check")))
      _ = assert(after.clues.find(_.id == ClueId("clue_mary_fish_time")).get.text.contains("大致先后"))
      _ = assert(after.clues.find(_.id == ClueId("clue_police_sound_check")).get.text.contains("没有看见声源"))
      _ = assertEquals(after.police.get.sharedClues,Set("clue_mary_sound","clue_mary_fish_time","clue_marple_sound").map(ClueId.apply))
    yield ()).unsafeRunSync()
  }

  test("fixed police comparison rolls back its receipt, disclosure and exchange together, then retries once") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"point_mary","route_mary_sound")
      _ <- read(url,id,"point_mary","route_mary_fish_time")
      _ <- read(url,id,"point_marple","route_marple_sound")
      before <- view(url,id)
      command=InvestigateMessage(owner,id,RequestId("fixed-police"),before.sequence,PointId("point_police"),RouteId("route_police_sound_check"))
      failed <- tx(url)(c => command.plan(c) *> IO.raiseError[Unit](new RuntimeException("force rollback"))).attempt
      _ = assert(failed.isLeft)
      rolledBack <- view(url,id)
      _ = assertEquals(rolledBack,before)
      result <- tx(url)(command.plan)
      retry <- tx(url)(command.plan)
      after <- view(url,id)
      _ = assertEquals(result,retry)
      _ = assertEquals(after.sequence.value,before.sequence.value+1)
      _ = assertEquals(after.police.get.exchanges.size,1)
      _ = assertEquals(after.police.get.sharedClues.size,3)
    yield ()).unsafeRunSync()
  }
