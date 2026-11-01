package services.cases.objects.investigation

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{WitnessAnswerId, WitnessId}

final case class ContinuousInterviewSpec(witness: WitnessId, statementAnswers: Set[WitnessAnswerId])
object ContinuousInterviewSpec:
  given Encoder[ContinuousInterviewSpec] = deriveEncoder[ContinuousInterviewSpec]
  given Decoder[ContinuousInterviewSpec] = deriveDecoder[ContinuousInterviewSpec]
