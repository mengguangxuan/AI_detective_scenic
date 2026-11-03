package services.games.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*
import services.cases.objects.timeline.{CaseTime, EventObserver, EventDisclosure}
import services.system.objects.RequestId

// Freeze only the information actually delivered to this recipient.
// Acquisition does not imply belief, public testimony, or disclosure to another person.
final case class EventObservationRecord(
  observation: EventObservationId, event: CaseEventId, recipient: EventObserver,
  acquiredAt: CaseTime, disclosure: EventDisclosure, request: RequestId
)
object EventObservationRecord:
  given Encoder[EventObservationRecord] = deriveEncoder[EventObservationRecord]
  given Decoder[EventObservationRecord] = deriveDecoder[EventObservationRecord]
