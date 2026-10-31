package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class GameSessionRecord(id: SessionId, owner: UserId, caseId: CaseId, caseRevision: Revision,
  stateSchemaVersion: Int, sequence: Sequence, storyTimeSeconds: Long,
  playerPlace: PlaceId, playerPosition: Vec3, opened: Set[EntityId], unlocked: Set[EntityId],
  visibleNodes: Set[SceneNodeId], completedOperations: Set[OperationId], clues: Set[ClueId], facts: Set[FactId], externalChecks: Vector[ExternalCheckProgress] = Vector.empty, conclusion: Option[CaseConclusionRecord] = None, reasoning: ReasoningProgress = ReasoningProgress.empty)

object GameSessionRecord:
  given Encoder[GameSessionRecord] = deriveEncoder[GameSessionRecord]
  given Decoder[GameSessionRecord] = deriveDecoder[GameSessionRecord].prepare(_.withFocus(_.mapObject { fields =>
    val checked = if fields.contains("externalChecks") then fields else fields.add("externalChecks",io.circe.Json.arr())
    if checked.contains("reasoning") then checked else checked.add("reasoning",io.circe.Json.obj("attempts" -> io.circe.Json.arr(),"ending" -> io.circe.Json.Null))
  }))
