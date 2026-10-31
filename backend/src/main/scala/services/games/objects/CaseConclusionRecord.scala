package services.games.objects

import io.circe.{Decoder,Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*
import services.system.objects.Sequence

final case class ConclusionCoverage(label: String, requiredClues: Set[ClueId], covered: Boolean)
object ConclusionCoverage:
  given Encoder[ConclusionCoverage] = deriveEncoder[ConclusionCoverage]
  given Decoder[ConclusionCoverage] = deriveDecoder[ConclusionCoverage]

final case class CaseConclusionRecord(sequence: Sequence, suspects: Set[PersonId], evidence: Set[ClueId], explanation: String,
  suspectScore: Int, evidenceScore: Int, coverage: Vector[ConclusionCoverage], review: String)
object CaseConclusionRecord:
  given Encoder[CaseConclusionRecord] = deriveEncoder[CaseConclusionRecord]
  given Decoder[CaseConclusionRecord] = deriveDecoder[CaseConclusionRecord]
