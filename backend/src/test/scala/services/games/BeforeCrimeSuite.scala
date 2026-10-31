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
import services.games.objects.InterviewReplyMode
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class BeforeCrimeSuite extends FunSuite:
  private val owner=UserId("before-crime-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId)=DatabaseSession.connection(url).use(c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(id: SessionId,sequence: Sequence,key: String)=
    InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),sequence,PointId("point_clement"),RouteId("route_clement_"+key))

  test("prior experiences are acquired by asking, preserve uncertainty and retry once without automatically informing police") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      first <- setup(url,id)
      _ = assertEquals(first.caseRevision,Revision(19))
      _ = assert(first.clues.isEmpty)
      _ = assert(!first.asJson.noSpaces.contains("周三傍晚"))
      command=read(id,first.sequence,"early_visit")
      receipt <- tx(url)(command.plan)
      retry <- tx(url)(command.plan)
      _ = assertEquals(retry,receipt)
      a <- view(url,id)
      _ = assertEquals(a.sequence,Sequence(1))
      _ = assert(a.clues.head.text.contains("目击范围"))
      _ <- tx(url)(read(id,a.sequence,"relationship").plan)
      b <- view(url,id)
      _ = assertEquals(b.clues.size,2)
      _ = assert(b.clues.exists(_.text.contains("不是他目击了杀人计划")))
      _ = assert(b.police.get.sharedClues.isEmpty)
      restored <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(restored,b)
    yield ()).unsafeRunSync()
  }

  test("only Clement receives his pre-crime recollections; another witness cannot grant those disclosure IDs") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    def model(clement: Boolean)=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assertEquals(input.user.contains("clement_relationship"),clement)
        assertEquals(input.user.contains("周四约15:45，你回到寓所"),clement)
        assert(!input.user.contains("authorTruth"))
        """{"reply":"我记得案发前的谈话。","disclosureIds":["clement_relationship"]}"""
      }
    (for
      _ <- setup(url,id)
      first <- view(url,id)
      _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),first.sequence,WitnessId("mary"),"牧师此前看见了什么？",Set.empty).run(url,model(false))
      rejected <- view(url,id)
      _ = assertEquals(rejected.interviews.last.mode,InterviewReplyMode.Rejected)
      _ = assert(rejected.clues.isEmpty)
      _ <- AskWitnessMessage(owner,id,RequestId(UUID.randomUUID().toString),rejected.sequence,WitnessId("clement"),"案发前你了解两人的关系吗？",Set.empty).run(url,model(true))
      acquired <- view(url,id)
      _ = assertEquals(acquired.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assertEquals(acquired.clues.map(_.id),Vector(ClueId("clue_clement_relationship")))
    yield ()).unsafeRunSync()
  }

  test("revision 18 preserves planter discovery without importing new recollections or changing its scene") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        old=latest.copy(revision=Revision(18),content=LegacyFixture.withoutBeforeCrime(latest.content))
        _ <- CaseRevisionTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId,old.revision).plan(c)
      yield ()))
      before <- view(url,id)
      _ = assertEquals(before.scene.revision,"5")
      _ = assert(before.scene.objects.exists(_.node == SceneNodeId("node_planter")))
      _ = assert(!before.points.flatMap(_.routes).exists(_.id == RouteId("route_clement_early_visit")))
      denied <- tx(url)(read(id,before.sequence,"early_visit").plan).attempt
      _ = assert(denied.isLeft)
      restored <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(restored,before)
    yield ()).unsafeRunSync()
  }
