package services.system.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class ReducerVersion(value: String)

object ReducerVersion:
  given Encoder[ReducerVersion] = deriveEncoder[ReducerVersion]
  given Decoder[ReducerVersion] = deriveDecoder[ReducerVersion]
