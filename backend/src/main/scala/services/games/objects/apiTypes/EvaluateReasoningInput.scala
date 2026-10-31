package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class EvaluateReasoningInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence,
  suspects: Set[PersonId], evidence: Set[ClueId], explanation: String)
object EvaluateReasoningInput:
  given Encoder[EvaluateReasoningInput] = deriveEncoder[EvaluateReasoningInput]
  given Decoder[EvaluateReasoningInput] = deriveDecoder[EvaluateReasoningInput]
