package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser
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

class NewWitnessSuite extends FunSuite:
  private val owner=UserId("new-witness-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(run: java.sql.Connection => IO[A]): IO[A] =
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(run(c)))
  private def setup(url: String,id: SessionId): IO[Unit] = DatabaseSession.connection(url).use { c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c) *> OpenStudyMessage(owner,id).plan(c)).void
  }
  private def client(raw: String)=new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String]=IO.pure(raw)
  private def ask(id: SessionId,witness: String,seq: Long=0,evidence: Set[ClueId]=Set.empty,request: String="ask") =
    AskWitnessMessage(owner,id,RequestId(request),Sequence(seq),WitnessId(witness),"请说明你的经历。",evidence)
  private def read(id: SessionId,point: String,route: String,seq: Long) =
    InvestigateMessage(owner,id,RequestId(route),Sequence(seq),PointId(point),RouteId(route))
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private val account="""{"reply":"我六点三刻来找牧师，和上校争执后开了枪。","disclosureIds":["redding_account"]}"""
  private val clock="""{"reply":"哦，你说那座钟。我动过它。","disclosureIds":["redding_clock"]}"""

  test("revision 4 games still restore their original cast and receipts after revision 5 is published") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c))
      _ <- tx(url) { c => for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)
        base = LegacyFixture.withoutFollowUps(latest.content)
        newPoints = Set(PointId("point_redding"),PointId("point_marple"),PointId("point_anne"),PointId("point_valet"),PointId("point_stone"),PointId("point_hartnell"),PointId("point_archer"))
        primaryClues = base.routes.filter(r => newPoints(r.point)).flatMap(_.clues).toSet
        newRoutes = base.routes.filter(r => newPoints(r.point) || r.requiredClues.intersect(primaryClues).nonEmpty)
        newClues = newRoutes.flatMap(_.clues).toSet
        oldContent = base.copy(publicPersons=Vector.empty,clock=None,externalChecks=Vector.empty,areaMap=None,
          witnesses=base.witnesses.filterNot(w => newPoints(w.point)).map(w => w.copy(answers=w.answers.filterNot(a => a.routes.exists(id => newRoutes.exists(_.id == id))))),
          points=base.points.filterNot(p => newPoints(p.id)),
          routes=base.routes.filterNot(r => newRoutes.exists(_.id == r.id)),
          operations=base.operations.filterNot(o => o.requiredClues.intersect(newClues).nonEmpty),
          clues=base.clues.filterNot(clue => newClues(clue.id)),
          stage=base.stage.map(_.copy(nextObjective=None)))
        previous = latest.copy(revision=Revision(4),content=oldContent)
        _ <- CaseRevisionTable.insert(c,previous)
        _ <- StartGameMessage(owner,id,previous.caseId,previous.revision).plan(c)
      yield () }
      result <- tx(url)(read(id,"point_clock","route_clock",0).plan)
      saved <- tx(url)(OpenStudyMessage(owner,id).plan)
      _ = assertEquals(saved.caseRevision,Revision(4))
      _ = assertEquals(saved.sequence,Sequence(1))
      _ = assertEquals(saved.witnesses.map(_.id).toSet,Set("clement","mary","melchett").map(WitnessId.apply))
      _ = assertEquals(saved.people.size,0)
      retry <- tx(url)(read(id,"point_clock","route_clock",0).plan)
      _ = assertEquals(retry,result)
      restored <- view(url,id)
      _ = assertEquals(restored,saved)
    yield ()).unsafeRunSync()
  }

  test("opening cast contains relationships without secret context or unearned testimony") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assertEquals(initial.caseRevision,Revision(15))
      _ = assertEquals(initial.people.size,14)
      _ = assertEquals(initial.witnesses.map(_.id).toSet,Set("clement","mary","redding","marple","melchett").map(WitnessId.apply))
      _ = assert(initial.people.find(_.id == PersonId("anne")).get.victimRelation.contains("妻子"))
      _ = assert(initial.people.find(_.id == PersonId("redding")).get.caseRelation.contains("自首"))
      _ = assert(initial.people.forall(p => p.identity.nonEmpty && p.victimRelation.nonEmpty && p.caseRelation.nonEmpty))
      _ = assertEquals(initial.clues.size,0)
      _ = assert(initial.points.forall(_.findings.isEmpty))
      _ = assert(!initial.asJson.noSpaces.contains("privateContext"))
      _ = assert(!initial.asJson.noSpaces.contains("共谋"))
      _ = assert(!initial.asJson.noSpaces.contains("18:45"))
    yield ()).unsafeRunSync()
  }

  test("Redding has his own private knowledge and outward policy; the player receives only his statement") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        val c=parser.parse(input.user).toOption.get.hcursor
        assertEquals(c.downField("witness").get[String]("name"),Right("劳伦斯·雷丁"))
        assert(c.get[Vector[String]]("privateContext").toOption.get.exists(_.contains("共谋")))
        assert(c.get[Vector[String]]("policy").toOption.get.exists(_.contains("不自动")))
        assert(!input.user.contains("marple_visitors"))
        account
      }
    (for
      _ <- setup(url,id)
      _ <- ask(id,"redding").run(url,model)
      saved <- view(url,id)
      _ = assert(saved.clues.find(_.id == ClueId("clue_redding_account")).get.text.contains("雷丁本人的供述"))
      _ = assert(!saved.asJson.noSpaces.contains("privateContext"))
      _ = assert(!saved.asJson.noSpaces.contains("共谋"))
      _ = assertEquals(saved.interviews.head.mode,InterviewReplyMode.Answered)
    yield ()).unsafeRunSync()
  }

  test("Redding's clock follow-up requires actually showing the acquired clock, not just owning it") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- ask(id,"redding").run(url,client(clock))
      _ <- tx(url)(read(id,"point_clock","route_clock",1).plan)
      _ <- ask(id,"redding",2,request="owned-only").run(url,client(clock))
      before <- view(url,id)
      _ = assert(before.interviews.forall(_.mode == InterviewReplyMode.Rejected))
      _ = assertEquals(before.clues.map(_.id),Vector(ClueId("clue_clock")))
      _ <- ask(id,"redding",3,Set(ClueId("clue_clock")),"shown").run(url,client(clock))
      after <- view(url,id)
      _ = assertEquals(after.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assert(after.clues.exists(_.id == ClueId("clue_redding_clock")))
    yield ()).unsafeRunSync()
  }

  test("medical challenge records Redding's response without forcing a recantation or a solved case") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- tx(url)(read(id,"point_police","route_police_medical",0).plan)
      _ <- ask(id,"redding",1,Set(ClueId("clue_medical_time"))).run(url,client(
        """{"reply":"我说的是六点三刻。我已经作了供述，没什么要补充的。","disclosureIds":["redding_medical_reply"]}"""))
      saved <- view(url,id)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_account")))
      _ = assert(saved.clues.exists(_.id == ClueId("clue_redding_medical_reply")))
      _ = assertEquals(saved.stage.get.completed,false)
      _ = assert(!saved.asJson.noSpaces.contains("clue_final"))
    yield ()).unsafeRunSync()
  }

  test("Marple receives only her observed scope; another witness cannot disclose it") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    val raw="""{"reply":"六点一刻过后我看见安妮经过，后来雷丁直接去了画室。我看不见书房里面。","disclosureIds":["marple_visitors"]}"""
    val model=new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String]=IO {
        assert(input.user.contains("看不见书房内部"))
        assert(!input.user.contains("你知道自己与安妮有私情"))
        assert(!input.user.contains("redding_medical_reply"))
        raw
      }
    (for
      _ <- setup(url,id)
      _ <- ask(id,"marple").run(url,model)
      _ <- ask(id,"mary",1,request="other-person").run(url,client(raw))
      saved <- view(url,id)
      _ = assertEquals(saved.interviews.map(_.mode),Vector(InterviewReplyMode.Answered,InterviewReplyMode.Rejected))
      _ = assertEquals(saved.clues.map(_.id),Vector(ClueId("clue_marple_visitors")))
    yield ()).unsafeRunSync()
  }

  test("Marple's response to a statement is gated by actual presentation, including fixed fallback") {
    val url=database; val id=SessionId(UUID.randomUUID().toString)
    val reply="""{"reply":"我没看见书房里面，只能说我在花园里看见的情况。","disclosureIds":["marple_account_reply"]}"""
    (for
      _ <- setup(url,id)
      _ <- ask(id,"redding").run(url,client(account))
      _ <- ask(id,"marple",1,request="not-shown").run(url,client(reply))
      _ <- tx(url)(read(id,"point_marple","route_marple_account_reply",2).plan)
      _ <- ask(id,"marple",3,request="remember-shown").run(url,client(reply))
      saved <- view(url,id)
      _ = assertEquals(saved.interviews(1).mode,InterviewReplyMode.Rejected)
      _ = assertEquals(saved.interviews.last.mode,InterviewReplyMode.Answered)
      _ = assert(saved.clues.exists(_.id == ClueId("clue_marple_account_reply")))
    yield ()).unsafeRunSync()
  }
