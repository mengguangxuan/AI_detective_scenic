package services.cases.objects.investigation

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.ClueId

// Author interpretation and its limits, disclosed only after an explicit ending.
final case class CaseReviewSection(title: String, explanation: String, relatedClues: Set[ClueId], boundary: String)
object CaseReviewSection:
  given Encoder[CaseReviewSection] = deriveEncoder[CaseReviewSection]
  given Decoder[CaseReviewSection] = deriveDecoder[CaseReviewSection]
