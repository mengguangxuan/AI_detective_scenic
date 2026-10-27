package services.games.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
import services.games.objects.apiTypes.*

final case class FixtureState(sequence: Sequence, playerPlace: PlaceId, playerPosition: Vec3, opened: Set[EntityId], unlocked: Set[EntityId], visibleNodes: Set[SceneNodeId], completedOperations: Set[OperationId], clues: Set[ClueId], facts: Set[FactId])

object FixtureState:
  given Encoder[FixtureState] = deriveEncoder[FixtureState]
  given Decoder[FixtureState] = deriveDecoder[FixtureState]
