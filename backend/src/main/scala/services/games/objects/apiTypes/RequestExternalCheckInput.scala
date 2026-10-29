package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.ExternalCheckId
final case class RequestExternalCheckInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence, check: ExternalCheckId)
object RequestExternalCheckInput:
  given Encoder[RequestExternalCheckInput] = deriveEncoder[RequestExternalCheckInput]
  given Decoder[RequestExternalCheckInput] = deriveDecoder[RequestExternalCheckInput]
