package services.cases.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*

// Closed state-update protocol, not a classification of narrative actions.
// These declarations grant no execution capability until an engine explicitly supports them.
enum EventWorldEffect:
  case SetPersonLife(person: PersonId, life: PersonLifeStatus)
  case SetPersonDetained(person: PersonId, detained: Boolean)
  case PlaceEntity(entity: EntityId, placement: EntityPlacement)
  case SetEntityOpened(entity: EntityId, opened: Boolean)
  case SetEntityUnlocked(entity: EntityId, unlocked: Boolean)
  case SetNodeVisible(node: SceneNodeId, visible: Boolean)

object EventWorldEffect:
  given Encoder[EventWorldEffect] = deriveEncoder[EventWorldEffect]
  given Decoder[EventWorldEffect] = deriveDecoder[EventWorldEffect]
