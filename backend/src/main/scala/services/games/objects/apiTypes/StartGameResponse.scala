package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class StartGameResponse(session: SessionId, sequence: Sequence)

object StartGameResponse:
  given Encoder[StartGameResponse] = deriveEncoder[StartGameResponse]
  given Decoder[StartGameResponse] = deriveDecoder[StartGameResponse]
