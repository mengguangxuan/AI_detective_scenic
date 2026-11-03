package services.games.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.timeline.CaseTime

enum EventPlanProgress:
  case Pending
  case Waiting(since: CaseTime, deadline: CaseTime)
  // Actual occurrence time is stored solely in EventOccurrence.
  case Completed
  case Cancelled(at: CaseTime, reason: String)
object EventPlanProgress:
  given Encoder[EventPlanProgress] = deriveEncoder[EventPlanProgress]
  given Decoder[EventPlanProgress] = deriveDecoder[EventPlanProgress]
