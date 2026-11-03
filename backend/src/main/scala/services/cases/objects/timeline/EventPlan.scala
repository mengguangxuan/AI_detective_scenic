package services.cases.objects.timeline

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.cases.objects.*

enum EventTrigger:
  case At(time: CaseTime)
  case AfterEvent(event: CaseEventId, delay: StoryDuration)
  // Evaluate requirements on entry into the window and on subsequent world changes.
  case When(window: TimeWindow)
object EventTrigger:
  given Encoder[EventTrigger] = deriveEncoder[EventTrigger]
  given Decoder[EventTrigger] = deriveDecoder[EventTrigger]

enum UnmetEventPolicy:
  case Cancel
  // Retry only while time remains; this never postpones an event silently.
  case WaitUntil(deadline: CaseTime)
object UnmetEventPolicy:
  given Encoder[UnmetEventPolicy] = deriveEncoder[UnmetEventPolicy]
  given Decoder[UnmetEventPolicy] = deriveDecoder[UnmetEventPolicy]

// order resolves simultaneous candidates; causedBy still constrains valid author ordering.
// Future alternatives use separate guarded plans/events, never mutate an occurred account.
final case class EventPlan(
  id: EventPlanId, event: CaseEventId, trigger: EventTrigger, order: Int,
  requirements: EventRequirements, onUnmet: UnmetEventPolicy,
  effects: Vector[EventWorldEffect], observations: Vector[EventObservation]
)
object EventPlan:
  given Encoder[EventPlan] = deriveEncoder[EventPlan]
  given Decoder[EventPlan] = deriveDecoder[EventPlan]
