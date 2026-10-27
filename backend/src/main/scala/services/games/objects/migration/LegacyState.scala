package services.games.objects.migration

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class LegacyState(sequence: Sequence, playerPlace: PlaceId, playerPosition: Vec3, opened: Set[EntityId],
  unlocked: Set[EntityId], visibleNodes: Set[SceneNodeId], completedOperations: Set[OperationId], clues: Set[ClueId], facts: Set[FactId])

object LegacyState:
  given Encoder[LegacyState] = deriveEncoder[LegacyState]
  given Decoder[LegacyState] = deriveDecoder[LegacyState]
