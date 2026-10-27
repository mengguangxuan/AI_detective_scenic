package services.system.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

final case class GameError(code: ErrorCode, publicMessage: String)
  extends RuntimeException(publicMessage)

object GameError:
  given Encoder[GameError] = deriveEncoder[GameError]
  given Decoder[GameError] = deriveDecoder[GameError]
