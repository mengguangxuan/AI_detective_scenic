package services.cases.objects.investigation

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*

final case class ConclusionEvidenceGroup(label: String, requiredClues: Set[ClueId])
object ConclusionEvidenceGroup:
  given Encoder[ConclusionEvidenceGroup] = deriveEncoder[ConclusionEvidenceGroup]
  given Decoder[ConclusionEvidenceGroup] = deriveDecoder[ConclusionEvidenceGroup]

// Author-only answer and rubric; never included in an unfinished player view or NPC context.
final case class CaseConclusionSpec(candidates: Set[PersonId], culprits: Set[PersonId], evidenceGroups: Vector[ConclusionEvidenceGroup], review: String, evaluation: Option[ReasoningEvaluationSpec] = None, reviewSections: Vector[CaseReviewSection] = Vector.empty)
object CaseConclusionSpec:
  private val encoder: Encoder[CaseConclusionSpec] = deriveEncoder[CaseConclusionSpec]
  given Encoder[CaseConclusionSpec] = encoder.mapJson(_.mapObject(o =>
    if o("reviewSections").exists(_.asArray.exists(_.isEmpty)) then o.remove("reviewSections") else o))
  given Decoder[CaseConclusionSpec] = deriveDecoder[CaseConclusionSpec].prepare(_.withFocus(_.mapObject(o =>
    if o.contains("reviewSections") then o else o.add("reviewSections",io.circe.Json.arr()))))
