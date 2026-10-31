package services.cases.objects.investigation

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class ReasoningCriterion(id: String, label: String, instruction: String)
object ReasoningCriterion:
  given Encoder[ReasoningCriterion] = deriveEncoder[ReasoningCriterion]
  given Decoder[ReasoningCriterion] = deriveDecoder[ReasoningCriterion]

// This truth is authorized only for the independent evaluator, never for NPCs or the guiding reply.
final case class ReasoningEvaluationSpec(truth: String, criteria: Vector[ReasoningCriterion])
object ReasoningEvaluationSpec:
  given Encoder[ReasoningEvaluationSpec] = deriveEncoder[ReasoningEvaluationSpec]
  given Decoder[ReasoningEvaluationSpec] = deriveDecoder[ReasoningEvaluationSpec]
