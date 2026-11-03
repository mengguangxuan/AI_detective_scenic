package services.cases

import io.circe.{Decoder, Encoder, parser}
import io.circe.syntax.*
import munit.FunSuite
import services.cases.objects.*
import services.cases.objects.timeline.*
import services.games.objects.timeline.*
import services.system.objects.{RequestId, Vec3}

class TimelineTypesSuite extends FunSuite:
  private val murder = CaseEventId("murder")
  private val music = CaseEventId("music")
  private val planId = EventPlanId("play_music")
  private val room = PlaceId("radio_room")
  private val person = PersonId("doctor")
  private val body = EntityId("doctor_body")
  private val fact = FactId("music_heard")
  private val request = RequestId("wait_request")
  private val observationId = EventObservationId("hear_music")
  private val world = TimelineWorldState(
    Map(person -> TimelinePersonState(body, PersonLifeStatus.Alive, false)),
    Map(body -> EntityPlacement(room, SceneNodeId("doctor_node"), Vec3(0, 0, 0))))
  private val heard = EventDisclosure("听见广播播放音乐。", Set(fact), Set.empty)
  private val observation = EventObservation(observationId, EventObserver.Player, ObservationAccess.AtPlace(room), heard)

  private def roundTrip[A: Encoder: Decoder](value: A): Unit =
    assertEquals(parser.decode[A](value.asJson.noSpaces), Right(value))

  test("story time preserves historical negatives and exact long values without wall-clock conversion") {
    roundTrip(CaseTime(-43200))
    roundTrip(CaseTime(Long.MaxValue))
    assert(parser.decode[CaseTime]("9223372036854775808").isLeft)
    assert(parser.decode[CaseTime]("1.5").isLeft)
  }

  test("time windows retain uncertainty, allow exact instants and reject reversed or wholly unknown bounds") {
    roundTrip(TimeWindow(Some(CaseTime(-3600)), None))
    roundTrip(TimeWindow(None, Some(CaseTime(600))))
    roundTrip(TimeWindow(Some(CaseTime(600)), Some(CaseTime(600))))
    assert(parser.decode[TimeWindow]("{\"earliest\":600,\"latest\":500}").isLeft)
    assert(parser.decode[TimeWindow]("{\"earliest\":null,\"latest\":null}").isLeft)
    assert(parser.decode[StoryDuration]("-1").isLeft)
    roundTrip(StoryDuration(0))
  }

  test("timeline IDs survive JSON map keys and reject empty or padded external references") {
    roundTrip(Map(murder -> CaseTime(-600)))
    roundTrip(Map(planId -> EventPlanProgress.Pending))
    assert(parser.decode[CaseEventId]("\" murder\"").isLeft)
    assert(parser.decode[Map[CaseEventId, CaseTime]]("{\"\":0}").isLeft)
    assert(parser.decode[EventPlanId]("\"\"").isLeft)
    assert(parser.decode[EventObservationId]("\"heard \"").isLeft)
  }

  test("authored events, initial history and future plans remain separate across serialization") {
    val event = CaseEvent(murder, "案前事件", TimeWindow(Some(CaseTime(-900)), Some(CaseTime(-600))),
      Set(person), Set.empty, Set(room), Set.empty, "前因", "固定经过", "结果", Set.empty, Set(FactId("private_truth")))
    val broadcast = CaseEvent(music, "广播", TimeWindow(Some(CaseTime(300)), Some(CaseTime(600))),
      Set.empty, Set.empty, Set(room), Set.empty, "预先安排", "播放录音", "音乐响起", Set(murder), Set(fact))
    val plan = EventPlan(planId, music, EventTrigger.AfterEvent(murder, StoryDuration(1200)), 0,
      EventRequirements.empty, UnmetEventPolicy.Cancel, Vector.empty, Vector(observation))
    val authored = CaseTimeline(Vector(event, broadcast), Map(murder -> CaseTime(-600)), world, Vector(plan))
    roundTrip(authored)
    assertEquals(authored.initialOccurrences.keySet, Set(murder))
    assertEquals(authored.plans.head.event, music)
  }

  test("all closed trigger, requirement, policy and world-effect variants preserve their parameters") {
    Vector[EventTrigger](EventTrigger.At(CaseTime(300)), EventTrigger.AfterEvent(murder, StoryDuration(60)),
      EventTrigger.When(TimeWindow(Some(CaseTime(300)), Some(CaseTime(600))))).foreach(roundTrip(_))
    Vector[UnmetEventPolicy](UnmetEventPolicy.Cancel, UnmetEventPolicy.WaitUntil(CaseTime(900))).foreach(roundTrip(_))
    val requirements = EventRequirements(Set(murder), Set(music), Set(OperationId("guard")),
      Vector(services.cases.objects.investigation.EntityOpenRequirement(EntityId("door"), false)),
      Vector(PersonRequirement(person, Some(PersonLifeStatus.Alive), Some(false), Some(room))), Map(body -> room))
    roundTrip(requirements)
    Vector[EventWorldEffect](
      EventWorldEffect.SetPersonLife(person, PersonLifeStatus.Dead),
      EventWorldEffect.SetPersonDetained(person, true),
      EventWorldEffect.PlaceEntity(body, EntityPlacement(room, SceneNodeId("body_node"), Vec3(1, 0, 2))),
      EventWorldEffect.SetEntityOpened(EntityId("door"), true),
      EventWorldEffect.SetEntityUnlocked(EntityId("door"), true),
      EventWorldEffect.SetNodeVisible(SceneNodeId("body_node"), true)).foreach(roundTrip(_))
    assert(parser.decode[EventTrigger]("{\"RunScript\":{\"code\":\"anything\"}}").isLeft)
    assert(parser.decode[EventWorldEffect]("{\"RunScript\":{}}").isLeft)
    assert(parser.decode[PersonLifeStatus]("\"Missing\"").isLeft)
  }

  test("occurrence time, acquisition time and actual disclosed content survive independently") {
    val occurrence = EventOccurrence(music, CaseTime(300), EventOccurrenceOrigin.Planned(planId, request))
    val delivered = EventObservationRecord(observationId, music, EventObserver.Person(person), CaseTime(480), heard, request)
    val state = TimelineState(Map(music -> occurrence), Map(planId -> EventPlanProgress.Completed), world, Vector(delivered))
    roundTrip(state)
    assertEquals(state.occurrences(music).at, CaseTime(300))
    assertEquals(state.observations.head.acquiredAt, CaseTime(480))
    assertEquals(state.observations.head.disclosure.confirmedFacts, Set(fact))
    assert(!state.observations.head.disclosure.confirmedFacts(FactId("private_truth")))
    assertEquals(state.observations.head.disclosure.fullClues, Set.empty[ClueId])
    roundTrip(EventOccurrence(murder, CaseTime(-600), EventOccurrenceOrigin.InitialHistory))
  }

  test("waiting and cancellation remain distinct from a completed event") {
    Vector[EventPlanProgress](EventPlanProgress.Pending, EventPlanProgress.Waiting(CaseTime(300), CaseTime(600)),
      EventPlanProgress.Completed, EventPlanProgress.Cancelled(CaseTime(600), "目标已受保护")).foreach(roundTrip(_))
    roundTrip(observation.copy(recipient = EventObserver.Person(person), access = ObservationAccess.Direct))
    roundTrip(EventDisclosure("完整获准材料", Set(fact), Set(ClueId("report"))))
  }
