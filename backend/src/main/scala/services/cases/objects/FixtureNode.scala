package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class FixtureNode(id: SceneNodeId, parent: Option[SceneNodeId], place: PlaceId, position: Vec3, initiallyVisible: Boolean)

object FixtureNode:
  given Encoder[FixtureNode] = deriveEncoder[FixtureNode]
  given Decoder[FixtureNode] = deriveDecoder[FixtureNode]
