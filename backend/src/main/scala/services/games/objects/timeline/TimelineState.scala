package services.games.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.{CaseEventId, EventPlanId}
import services.cases.objects.timeline.TimelineWorldState

// story time and sequence remain solely in GameSessionRecord when this is integrated.
// Occurrence/progress maps are authoritative; observations preserve disclosure history.
final case class TimelineState(
  occurrences: Map[CaseEventId, EventOccurrence],
  plans: Map[EventPlanId, EventPlanProgress],
  world: TimelineWorldState,
  observations: Vector[EventObservationRecord]
)
object TimelineState:
  given Encoder[TimelineState] = deriveEncoder[TimelineState]
  given Decoder[TimelineState] = deriveDecoder[TimelineState]
