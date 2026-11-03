package services.cases.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*
import services.system.objects.Vec3

enum PersonLifeStatus:
  case Alive, Dead
object PersonLifeStatus:
  given Encoder[PersonLifeStatus] = Encoder.encodeString.contramap(_.toString)
  given Decoder[PersonLifeStatus] = Decoder.decodeString.emap(value =>
    PersonLifeStatus.values.find(_.toString == value).toRight("Unknown person life status"))

// The body entity is the sole location authority for this person.
final case class TimelinePersonState(body: EntityId, life: PersonLifeStatus, detained: Boolean)
object TimelinePersonState:
  given Encoder[TimelinePersonState] = deriveEncoder[TimelinePersonState]
  given Decoder[TimelinePersonState] = deriveDecoder[TimelinePersonState]

// position is local to the registered node's parent (as in FixtureNode).
// A placement must move the entity and its presentation binding together.
final case class EntityPlacement(place: PlaceId, node: SceneNodeId, position: Vec3)
object EntityPlacement:
  given Encoder[EntityPlacement] = deriveEncoder[EntityPlacement]
  given Decoder[EntityPlacement] = deriveDecoder[EntityPlacement]

// Initial snapshot in a definition, current authoritative snapshot in a session.
// Open/unlocked entities and node visibility remain in the existing GameSessionRecord.
final case class TimelineWorldState(
  persons: Map[PersonId, TimelinePersonState],
  entities: Map[EntityId, EntityPlacement]
)
object TimelineWorldState:
  given Encoder[TimelineWorldState] = deriveEncoder[TimelineWorldState]
  given Decoder[TimelineWorldState] = deriveDecoder[TimelineWorldState]
