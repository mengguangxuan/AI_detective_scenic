package services.cases.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*

enum EventObserver:
  case Player
  case Person(person: PersonId)
object EventObserver:
  given Encoder[EventObserver] = deriveEncoder[EventObserver]
  given Decoder[EventObserver] = deriveDecoder[EventObserver]

enum ObservationAccess:
  case Direct
  case AtPlace(place: PlaceId)
object ObservationAccess:
  given Encoder[ObservationAccess] = deriveEncoder[ObservationAccess]
  given Decoder[ObservationAccess] = deriveDecoder[ObservationAccess]

// Actual permitted text and confirmed facts. fullClues grants the entire registered material;
// partial hearing belongs in text without granting that clue. Never expand fullClues into all facts.
final case class EventDisclosure(text: String, confirmedFacts: Set[FactId], fullClues: Set[ClueId])
object EventDisclosure:
  given Encoder[EventDisclosure] = deriveEncoder[EventDisclosure]
  given Decoder[EventDisclosure] = deriveDecoder[EventDisclosure]

// Direct is an explicitly authored delivery/observation, not a worldwide broadcast.
// AtPlace is evaluated against the recipient's actual location at this event.
final case class EventObservation(
  id: EventObservationId, recipient: EventObserver, access: ObservationAccess,
  disclosure: EventDisclosure
)
object EventObservation:
  given Encoder[EventObservation] = deriveEncoder[EventObservation]
  given Decoder[EventObservation] = deriveDecoder[EventObservation]
