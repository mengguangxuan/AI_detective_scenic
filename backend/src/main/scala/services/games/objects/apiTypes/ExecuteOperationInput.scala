package services.games.objects.apiTypes
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*
final case class ExecuteOperationInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence, operation: OperationId)
object ExecuteOperationInput:
  given Encoder[ExecuteOperationInput] = deriveEncoder[ExecuteOperationInput]
  given Decoder[ExecuteOperationInput] = deriveDecoder[ExecuteOperationInput]
