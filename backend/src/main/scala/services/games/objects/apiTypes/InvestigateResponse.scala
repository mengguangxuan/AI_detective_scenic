package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class InvestigateResponse(result: CommandResult)

object InvestigateResponse:
  given Encoder[InvestigateResponse] = deriveEncoder[InvestigateResponse]
  given Decoder[InvestigateResponse] = deriveDecoder[InvestigateResponse]
