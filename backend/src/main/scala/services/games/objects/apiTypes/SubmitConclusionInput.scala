package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class SubmitConclusionInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence,
  suspects: Set[PersonId], evidence: Set[ClueId], explanation: String)
object SubmitConclusionInput:
  given Encoder[SubmitConclusionInput] = deriveEncoder[SubmitConclusionInput]
  given Decoder[SubmitConclusionInput] = deriveDecoder[SubmitConclusionInput]
