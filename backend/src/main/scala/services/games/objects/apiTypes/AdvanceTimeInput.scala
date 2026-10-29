package services.games.objects.apiTypes
import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
final case class AdvanceTimeInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence, minutes: Int)
object AdvanceTimeInput:
  given Encoder[AdvanceTimeInput] = deriveEncoder[AdvanceTimeInput]
  given Decoder[AdvanceTimeInput] = deriveDecoder[AdvanceTimeInput]
