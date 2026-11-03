package services.cases.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.CaseEventId

// Separate author/runtime contract; not yet an accepted field of the legacy fixture format.
// Initial world is already the result of history: do not replay initial occurrences as effects.
final case class CaseTimeline(
  events: Vector[CaseEvent], initialOccurrences: Map[CaseEventId, CaseTime],
  initialWorld: TimelineWorldState, plans: Vector[EventPlan]
)
object CaseTimeline:
  given Encoder[CaseTimeline] = deriveEncoder[CaseTimeline]
  given Decoder[CaseTimeline] = deriveDecoder[CaseTimeline]
