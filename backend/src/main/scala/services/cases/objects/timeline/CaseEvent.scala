package services.cases.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*

// Author-only semantic account. A future candidate becomes actual history only on occurrence.
// process/outcome are never interpreted as executable instructions; listed actors are not exhaustive.
final case class CaseEvent(
  id: CaseEventId, title: String, when: TimeWindow,
  actors: Set[PersonId], targets: Set[EntityId], places: Set[PlaceId], tools: Set[EntityId],
  background: String, process: String, outcome: String,
  causedBy: Set[CaseEventId], resultingFacts: Set[FactId]
)
object CaseEvent:
  given Encoder[CaseEvent] = deriveEncoder[CaseEvent]
  given Decoder[CaseEvent] = deriveDecoder[CaseEvent]
