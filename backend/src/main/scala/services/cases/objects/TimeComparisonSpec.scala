package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// Fixed authoring answer stays on the server. The player supplies evidence and a calculation.
final case class TimeComparisonSpec(point: PointId, route: RouteId, requiredClues: Set[ClueId], evidenceClues: Set[ClueId],
  convertedTimeMinutes: Int, assessment: TimeAssessment)
object TimeComparisonSpec:
  given Encoder[TimeComparisonSpec] = deriveEncoder[TimeComparisonSpec]
  given Decoder[TimeComparisonSpec] = deriveDecoder[TimeComparisonSpec]
