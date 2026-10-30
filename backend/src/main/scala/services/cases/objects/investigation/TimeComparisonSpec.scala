package services.cases.objects.investigation

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{ClueId, PointId, RouteId}

// Fixed authoring answer stays on the server. The player supplies evidence and a calculation.
final case class TimeComparisonSpec(point: PointId, route: RouteId, requiredClues: Set[ClueId], evidenceClues: Set[ClueId],
  convertedTimeMinutes: Int, assessment: TimeAssessment)
object TimeComparisonSpec:
  given Encoder[TimeComparisonSpec] = deriveEncoder[TimeComparisonSpec]
  given Decoder[TimeComparisonSpec] = deriveDecoder[TimeComparisonSpec]
