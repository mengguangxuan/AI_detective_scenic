package services.games.utils

import io.circe.{Decoder,Json}
import io.circe.syntax.*
import scala.util.Try
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.migration.*
import services.system.objects.*
import services.cases.objects.investigation.FixtureEffect

// Used only once to translate the old fixture-v1 protocol, never by normal state queries.
private[games] object LegacyMigrationRules:
  private[games] def convert(old: LegacySessionSnapshot,commits: Vector[LegacyCommitSnapshot],revision: CaseRevisionRecord): Either[GameError,Vector[ActionPlan]] =
    Try {
      var state=FixtureRules.initial(revision,old.id,old.owner)
      val original=old.initialState
      require(original.sequence==Sequence(0) && original.playerPlace==state.playerPlace && original.playerPosition==state.playerPosition &&
        original.opened==state.opened && original.unlocked==state.unlocked && original.visibleNodes==state.visibleNodes &&
        original.completedOperations==state.completedOperations && original.clues==state.clues && original.facts==state.facts,"Legacy initial state mismatch")
      val plans=commits.map { commit =>
        require(commit.session==old.id && commit.reducerVersion=="fixture-v1" && commit.sequence.value==state.sequence.value+1,"Legacy commit order/version mismatch")
        require(commit.changes.size==2,"Unsupported legacy commit shape")
        def field[A: Decoder](json: Json,key: String): A=json.hcursor.get[A](key).fold(throw _,identity)
        val first=commit.changes.head
        val last=commit.changes.last
        val plan=(field[String](first,"kind"),field[String](last,"kind")) match
          case ("WorldChanged","OperationCompleted") =>
            val operation=field[OperationId](last,"operation")
            val effects=field[Vector[FixtureEffect]](first,"effects")
            require(revision.content.operations.find(_.id==operation).exists(_.effects==effects),"Legacy effects mismatch")
            FixtureRules.execute(revision.content,state,commit.request,operation).fold(throw _,identity)
          case ("Investigated","Acquired") =>
            val record=field[Json](first,"record")
            val route=field[RouteId](record,"route")
            val expected=FixtureRules.investigate(revision.content,state,commit.request,field[PointId](record,"point"),route).fold(throw _,identity)
            val acquisition=field[Json](last,"record")
            require(field[Set[FactId]](acquisition,"facts")==expected.record.disclosedFacts)
            require(field[Set[ClueId]](acquisition,"clues")==expected.record.disclosedClues)
            require(field[RecordId](acquisition,"sourceRecord")==expected.record.id)
            require(field[RecordId](record,"id")==expected.record.id && field[Text](record,"text").value==expected.record.text)
            expected
          case _ => throw new IllegalArgumentException("Unsupported legacy changes")
        state=plan.session
        plan
      }
      require(state.sequence==old.sequence,"Legacy final sequence mismatch")
      plans
    }.toEither.left.map(error => GameError(ErrorCode.InvalidValue,s"旧会话迁移校验失败：${error.getMessage}"))
