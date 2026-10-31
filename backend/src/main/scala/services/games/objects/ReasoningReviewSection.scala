package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*

final case class ReasoningReviewSection(title: String, explanation: String, boundary: String, materials: Vector[ReviewMaterial])
object ReasoningReviewSection:
  given Encoder[ReasoningReviewSection] = deriveEncoder[ReasoningReviewSection]
  given Decoder[ReasoningReviewSection] = deriveDecoder[ReasoningReviewSection]
