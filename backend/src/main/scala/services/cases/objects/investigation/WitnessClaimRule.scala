package services.cases.objects.investigation

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{WitnessId,ClueId}

// Authored reasoning constraints, not a phase or a prescribed utterance.
final case class WitnessClaimRule(witness: WitnessId, clue: ClueId, priorStatement: Option[ClueId], basisInstruction: String)
object WitnessClaimRule:
  given Encoder[WitnessClaimRule] = deriveEncoder[WitnessClaimRule]
  given Decoder[WitnessClaimRule] = deriveDecoder[WitnessClaimRule]
