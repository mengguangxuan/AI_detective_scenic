package services.system.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class Sequence(value: Long)

object Sequence:
  given Encoder[Sequence] = deriveEncoder[Sequence]
  given Decoder[Sequence] = deriveDecoder[Sequence]
