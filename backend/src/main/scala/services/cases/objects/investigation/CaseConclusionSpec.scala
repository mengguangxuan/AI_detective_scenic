package services.cases.objects.investigation

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*

final case class ConclusionEvidenceGroup(label: String, requiredClues: Set[ClueId])
object ConclusionEvidenceGroup:
  given Encoder[ConclusionEvidenceGroup] = deriveEncoder[ConclusionEvidenceGroup]
  given Decoder[ConclusionEvidenceGroup] = deriveDecoder[ConclusionEvidenceGroup]

// Author-only answer and rubric; never included in an unfinished player view or NPC context.
final case class CaseConclusionSpec(candidates: Set[PersonId], culprits: Set[PersonId], evidenceGroups: Vector[ConclusionEvidenceGroup], review: String)
object CaseConclusionSpec:
  given Encoder[CaseConclusionSpec] = deriveEncoder[CaseConclusionSpec]
  given Decoder[CaseConclusionSpec] = deriveDecoder[CaseConclusionSpec]
