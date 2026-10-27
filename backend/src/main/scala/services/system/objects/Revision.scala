package services.system.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class Revision(value: Int)

object Revision:
  given Encoder[Revision] = deriveEncoder[Revision]
  given Decoder[Revision] = deriveDecoder[Revision]
