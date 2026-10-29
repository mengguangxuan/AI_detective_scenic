package services.games.objects.apiTypes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*

final case class AskWitnessInput(sessionId: SessionId, requestId: RequestId, expectedSequence: Sequence, witness: WitnessId, question: String, evidence: Set[ClueId])
object AskWitnessInput:
  given Encoder[AskWitnessInput] = deriveEncoder[AskWitnessInput]
  given Decoder[AskWitnessInput] = deriveDecoder[AskWitnessInput]
