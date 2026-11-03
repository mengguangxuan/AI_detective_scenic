package services.games.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*
import services.cases.objects.timeline.CaseTime
import services.system.objects.RequestId

enum EventOccurrenceOrigin:
  case InitialHistory
  case Planned(plan: EventPlanId, request: RequestId)
object EventOccurrenceOrigin:
  given Encoder[EventOccurrenceOrigin] = deriveEncoder[EventOccurrenceOrigin]
  given Decoder[EventOccurrenceOrigin] = deriveDecoder[EventOccurrenceOrigin]

// Actual objective time, distinct from acquisition time and system persistence time.
// Stored as author-only history; never copied wholesale into a player journal.
final case class EventOccurrence(event: CaseEventId, at: CaseTime, origin: EventOccurrenceOrigin)
object EventOccurrence:
  given Encoder[EventOccurrence] = deriveEncoder[EventOccurrence]
  given Decoder[EventOccurrence] = deriveDecoder[EventOccurrence]
