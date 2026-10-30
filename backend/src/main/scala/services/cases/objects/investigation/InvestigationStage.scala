package services.cases.objects.investigation

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId

// Authored goal for an investigation slice; completion does not resolve the case.
final case class InvestigationStage(introduction: String, objective: String, completionClue: ClueId,
  comparison: Option[TimeComparisonSpec] = None, nextObjective: Option[String] = None)
object InvestigationStage:
  given Encoder[InvestigationStage] = deriveEncoder[InvestigationStage]
  given Decoder[InvestigationStage] = deriveDecoder[InvestigationStage]
