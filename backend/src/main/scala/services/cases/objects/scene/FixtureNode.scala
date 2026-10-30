package services.cases.objects.scene

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.Vec3
import services.cases.objects.{PlaceId, SceneNodeId}

final case class FixtureNode(id: SceneNodeId, parent: Option[SceneNodeId], place: PlaceId, position: Vec3, initiallyVisible: Boolean)

object FixtureNode:
  given Encoder[FixtureNode] = deriveEncoder[FixtureNode]
  given Decoder[FixtureNode] = deriveDecoder[FixtureNode]
