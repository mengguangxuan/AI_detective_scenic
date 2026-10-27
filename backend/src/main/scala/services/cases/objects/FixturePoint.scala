package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class FixturePoint(id: PointId, node: SceneNodeId, localPoint: Vec3, radiusMeters: Double)

object FixturePoint:
  given Encoder[FixturePoint] = deriveEncoder[FixturePoint]
  given Decoder[FixturePoint] = deriveDecoder[FixturePoint]
