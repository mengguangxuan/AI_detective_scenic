package services.system.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class Vec3(x: Double, y: Double, z: Double)

object Vec3:
  given Encoder[Vec3] = deriveEncoder[Vec3]
  given Decoder[Vec3] = deriveDecoder[Vec3]
