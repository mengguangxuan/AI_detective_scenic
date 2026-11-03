package services.games

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import java.util.UUID
import munit.FunSuite
import services.cases.api.ImportCaseMessage
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.games.api.*
import services.games.objects.*
import services.games.utils.{ConclusionRules,FixtureRules,InterviewRules}
import services.games.tables.SessionTable.SessionTable
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.*
import services.system.objects.*

class ConclusionSuite extends FunSuite:
  private val owner=UserId("conclusion-owner")
  private def database=s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000"
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A]=
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))
  private def setup(url: String,id: SessionId): IO[Unit]=DatabaseSession.connection(url).use(c =>
    DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      (for
        latest <- ImportCaseMessage(Paths.get("../docs/fixtures/investigation_base.json")).plan(c)
        old=latest.copy(caseId=CaseId("coverage_case"),content=CaseContentSamples.coverageOnly(latest.content))
        _ <- CaseTable.insert(c,old)
        _ <- StartGameMessage(owner,id,old.caseId).plan(c)
      yield ())).void)
  private def view(url: String,id: SessionId)=tx(url)(GetInvestigationSnapshotMessage(owner,id).plan)
  private def read(url: String,id: SessionId,point: String,route: String): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(InvestigateMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,PointId(point),RouteId(route)).plan)
  yield ()
  private def request(url: String,id: SessionId,check: String): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(RequestExternalCheckMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,ExternalCheckId(check)).plan)
  yield ()
  private def waitFor(url: String,id: SessionId,minutes: Int): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(AdvanceTimeMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,minutes).plan)
  yield ()
  private val explanation="  这是我对人物动机、行动经过以及现有材料关系的推理。\n"
  private val both=Set(PersonId("anne"),PersonId("redding"))
  private val oneClue=Set(ClueId("clue_call"))
  private def submit(url: String,id: SessionId,suspects: Set[PersonId]=both,evidence: Set[ClueId]=oneClue,text: String=explanation): IO[Unit]=for
    s <- view(url,id)
    _ <- tx(url)(SubmitConclusionMessage(owner,id,RequestId(UUID.randomUUID().toString),s.sequence,suspects,evidence,text).plan)
  yield ()
  private def code(value: Either[Throwable,?])=value.left.toOption.collect { case e: GameError => e.code }

  test("author rubric and review stay private; unearned references, missing fields, foreign identities and stale state cannot submit") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      initial <- view(url,id)
      _ = assert(initial.conclusion.isEmpty && initial.conclusionCandidates.nonEmpty)
      _ = assert(!initial.asJson.noSpaces.contains("作者复盘"))
      _ = assert(!initial.asJson.noSpaces.contains("requiredClues"))
      _ = assert(!initial.asJson.noSpaces.contains("共同策划杀害"))
      unearned <- submit(url,id).attempt
      _ = assertEquals(code(unearned),Some(ErrorCode.AccessDenied))
      empty <- submit(url,id,Set.empty).attempt
      _ = assertEquals(code(empty),Some(ErrorCode.InvalidValue))
      short <- submit(url,id,text="短文").attempt
      _ = assertEquals(code(short),Some(ErrorCode.InvalidValue))
      unknown <- submit(url,id,Set(PersonId("not-a-person"))).attempt
      _ = assertEquals(code(unknown),Some(ErrorCode.InvalidValue))
      _ <- read(url,id,"point_clement","route_call")
      s <- view(url,id)
      foreign <- tx(url)(SubmitConclusionMessage(UserId("foreign"),id,RequestId("foreign"),s.sequence,both,oneClue,explanation).plan).attempt
      _ = assertEquals(code(foreign),Some(ErrorCode.AccessDenied))
      stale <- tx(url)(SubmitConclusionMessage(owner,id,RequestId("stale"),Sequence(0),both,oneClue,explanation).plan).attempt
      _ = assertEquals(code(stale),Some(ErrorCode.StaleState))
      after <- view(url,id)
      _ = assertEquals(after,s)
    yield ()).unsafeRunSync()
  }

  test("scores depend on exact suspects and actually cited groups; arbitrary text and possession alone are not semantic proof") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      latest <- tx(url)(ImportCaseMessage(Paths.get("../docs/fixtures/investigation_base.json")).plan)
      r=latest.copy(caseId=CaseId("coverage_case"),content=CaseContentSamples.coverageOnly(latest.content))
      spec=r.content.conclusion.get
      all=spec.evidenceGroups.flatMap(_.requiredClues).toSet
      state=FixtureRules.initial(r,id,owner).copy(clues=all)
      full=ConclusionRules.submit(r.content,state,RequestId("full"),both,all,explanation).toOption.get.session.conclusion.get
      _ = assertEquals(full.suspectScore,50)
      _ = assertEquals(full.evidenceScore,50)
      _ = assert(full.coverage.forall(_.covered))
      possession=ConclusionRules.submit(r.content,state,RequestId("possession"),both,Set(ClueId("clue_clock")),"随便写一段没有论证的文字也不会被当成语义正确。").toOption.get.session.conclusion.get
      _ = assertEquals(possession.evidenceScore,0)
      partial=ConclusionRules.submit(r.content,state,RequestId("partial"),Set(PersonId("anne")),all,explanation).toOption.get.session.conclusion.get
      _ = assertEquals(partial.suspectScore,25)
      wrong=ConclusionRules.submit(r.content,state,RequestId("wrong"),spec.candidates,all,explanation).toOption.get.session.conclusion.get
      _ = assertEquals(wrong.suspectScore,0)
      _ = assertEquals(wrong.explanation,explanation)
    yield ()).unsafeRunSync()
  }

  test("submission, receipt and journal roll back together; lost responses retry exactly without replacing the frozen attempt") {
    val url=s"jdbc:h2:file:${Files.createTempDirectory("conclusion-").resolve("case")};LOCK_TIMEOUT=3000"
    val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"point_clement","route_call")
      first <- view(url,id)
      command=SubmitConclusionMessage(owner,id,RequestId("final-attempt"),first.sequence,both,oneClue,explanation)
      failed <- tx(url)(c => command.plan(c) *> IO.raiseError[Unit](new RuntimeException("rollback"))).attempt
      _ = assert(failed.isLeft)
      unchanged <- view(url,id)
      _ = assertEquals(unchanged,first)
      accepted <- tx(url)(command.plan)
      repeated <- tx(url)(command.plan)
      _ = assertEquals(repeated,accepted)
      conflict <- tx(url)(command.copy(explanation=explanation+"改变内容").plan).attempt
      _ = assertEquals(code(conflict),Some(ErrorCode.RequestConflict))
      saved <- view(url,id)
      _ = assertEquals(saved.sequence.value,first.sequence.value+1)
      _ = assertEquals(saved.conclusion.get.explanation,explanation)
      _ = assertEquals(saved.conclusion.get.evidence,oneClue)
      _ = assertEquals(saved.clues,first.clues)
      _ = assert(!saved.stage.get.completed)
      _ <- read(url,id,"point_mary","route_mary_sound")
      after <- view(url,id)
      _ = assertEquals(after.conclusion,saved.conclusion)
      duplicate <- submit(url,id).attempt
      _ = assertEquals(code(duplicate),Some(ErrorCode.ConditionFailed))
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      restored <- view(url,id)
      retry <- tx(url)(command.plan)
      _ = assertEquals(restored,after)
      _ = assertEquals(retry,accepted)
    yield ()).unsafeRunSync()
  }

  test("review never becomes an acquired clue, police evidence, or regular witness knowledge") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- setup(url,id)
      _ <- read(url,id,"point_clement","route_call")
      _ <- submit(url,id)
      state <- tx(url)(SessionTable.find(_,id)).map(_.get)
      caseRecord <- tx(url)(CaseTable.find(_,state.caseId)).map(_.get)
      context=InterviewRules.context(caseRecord.content,state,WitnessId("mary"),"你知道什么？",Set.empty,Vector.empty,Vector.empty).toOption.get
      _ = assert(!context.asJson.noSpaces.contains("作者复盘"))
      _ = assert(!context.asJson.noSpaces.contains("共同策划杀害"))
      saved <- view(url,id)
      _ = assert(saved.police.get.sharedClues.isEmpty)
      _ = assertEquals(saved.clues.size,1)
    yield ()).unsafeRunSync()
  }


  test("coverage scoring accepts independent group counts and uses the covered proportion") {
    val url=database;val id=SessionId(UUID.randomUUID().toString)
    (for
      _ <- DatabaseSession.connection(url).use(DatabaseSchema.initialize)
      original <- tx(url)(ImportCaseMessage(Paths.get("../docs/fixtures/investigation_base.json")).plan)
      _ <- IO {
        Vector(2,3,7).foreach { count =>
          val groups=original.content.clues.take(count).zipWithIndex.map { (clue,index) =>
            services.cases.objects.investigation.ConclusionEvidenceGroup(s"材料组$index",Set(clue.id))
          }
          val content=CaseContentSamples.coverageOnly(original.content).copy(conclusion=original.content.conclusion.map(_.copy(evaluation=None,evidenceGroups=groups)))
          val record=original.copy(content=content)
          val evidence=groups.take(1).flatMap(_.requiredClues).toSet
          val state=FixtureRules.initial(record,id,owner).copy(clues=evidence)
          val outcome=ConclusionRules.submit(content,state,RequestId("coverage"),both,evidence,explanation).toOption.get.session.conclusion.get
          assertEquals(outcome.evidenceScore,50/count)
          assertEquals(outcome.coverage.size,count)
        }
      }
    yield ()).unsafeRunSync()
  }
