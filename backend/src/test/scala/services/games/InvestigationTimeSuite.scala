package services.games

import java.time.OffsetDateTime
import munit.FunSuite
import services.cases.objects.*
import services.cases.objects.investigation.InvestigationClock
import services.games.utils.ExternalCheckRules
import services.system.objects.Vec3

class InvestigationTimeSuite extends FunSuite:
  private val initial=7200L
  private val base=CaseContent(PlaceId("origin"),"起点",initial,"夜间调查",Vec3(0,0,0),
    Vector.empty,Vector.empty,Vector.empty,Vector.empty,Vector.empty,Vector.empty,Set.empty,Set.empty,Set.empty,
    clock=Some(InvestigationClock(23*60+55,60)),sceneBundle="study")

  test("unknown calendar uses the authored origin and relative days without inventing Friday") {
    assertEquals(ExternalCheckRules.timeLabel(base,initial),"夜间调查 23:55")
    assertEquals(ExternalCheckRules.timeLabel(base,initial+600),"夜间调查起 +1天 00:05")
    assertEquals(ExternalCheckRules.timeLabel(base,initial+8*86400),"夜间调查起 +8天 23:55")
    assertEquals(ExternalCheckRules.timeLabel(base.copy(clock=None),initial+600),"夜间调查 +600秒")
  }

  test("an explicit weekday rolls over midnight, wraps weeks and respects a nonzero initial story time") {
    val content=base.copy(clock=Some(InvestigationClock(23*60+55,60,Some(3))))
    assertEquals(ExternalCheckRules.timeLabel(content,initial),"周三 23:55")
    assertEquals(ExternalCheckRules.timeLabel(content,initial+600),"周四 00:05")
    assertEquals(ExternalCheckRules.timeLabel(content,initial+8*86400),"周四 23:55")
    assertEquals(ExternalCheckRules.timeLabel(content,initial-86400),"周二 23:55")
  }

  test("calendar origin anchors story zero, preserves its offset and crosses year boundaries without local timezone inference") {
    val content=base.copy(calendarOrigin=Some(OffsetDateTime.parse("1891-12-31T21:55:00+01:00")))
    assertEquals(ExternalCheckRules.timeLabel(content,initial),"1891-12-31 23:55")
    assertEquals(ExternalCheckRules.timeLabel(content,initial+600),"1892-01-01 00:05")
    val other=content.copy(calendarOrigin=Some(OffsetDateTime.parse("1891-12-31T21:55:00-05:00")))
    assertEquals(ExternalCheckRules.timeLabel(other,initial+600),"1892-01-01 00:05")
  }
