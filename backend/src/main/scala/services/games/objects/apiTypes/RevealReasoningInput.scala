package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class RevealReasoningInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence,
  abandon: Boolean)
object RevealReasoningInput:
  given Encoder[RevealReasoningInput] = deriveEncoder[RevealReasoningInput]
  given Decoder[RevealReasoningInput] = deriveDecoder[RevealReasoningInput]
