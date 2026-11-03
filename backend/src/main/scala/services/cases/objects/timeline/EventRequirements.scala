package services.cases.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*
import services.cases.objects.investigation.EntityOpenRequirement

final case class PersonRequirement(
  person: PersonId, life: Option[PersonLifeStatus], detained: Option[Boolean], place: Option[PlaceId]
)
object PersonRequirement:
  given Encoder[PersonRequirement] = deriveEncoder[PersonRequirement]
  given Decoder[PersonRequirement] = deriveDecoder[PersonRequirement]

// Conjunction of explicit world predicates. Empty means unconditional.
// absentEvents means "has not occurred yet", never "was disproved".
// Player/NPC knowledge is intentionally not treated as objective world state.
final case class EventRequirements(
  occurredEvents: Set[CaseEventId], absentEvents: Set[CaseEventId],
  completedOperations: Set[OperationId], opened: Vector[EntityOpenRequirement],
  persons: Vector[PersonRequirement], entityPlaces: Map[EntityId, PlaceId]
)
object EventRequirements:
  val empty: EventRequirements = EventRequirements(Set.empty, Set.empty, Set.empty, Vector.empty, Vector.empty, Map.empty)
  given Encoder[EventRequirements] = deriveEncoder[EventRequirements]
  given Decoder[EventRequirements] = deriveDecoder[EventRequirements]
