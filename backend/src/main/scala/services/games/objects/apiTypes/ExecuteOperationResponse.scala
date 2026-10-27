package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class ExecuteOperationResponse(result: CommandResult)

object ExecuteOperationResponse:
  given Encoder[ExecuteOperationResponse] = deriveEncoder[ExecuteOperationResponse]
  given Decoder[ExecuteOperationResponse] = deriveDecoder[ExecuteOperationResponse]
