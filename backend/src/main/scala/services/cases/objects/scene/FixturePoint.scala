package services.cases.objects.scene

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.Vec3
import services.cases.objects.{PointId, SceneNodeId}


final case class FixturePoint(id: PointId, node: SceneNodeId, localPoint: Vec3, radiusMeters: Double, label: String)

object FixturePoint:
  given Encoder[FixturePoint] = deriveEncoder[FixturePoint]
  given Decoder[FixturePoint] = deriveDecoder[FixturePoint]
