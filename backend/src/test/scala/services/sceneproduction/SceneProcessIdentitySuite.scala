package services.sceneproduction
import java.time.Instant
import munit.FunSuite
import services.sceneproduction.utils.SceneProcessIdentity
class SceneProcessIdentitySuite extends FunSuite:
  private val start=Instant.parse("2026-11-04T11:47:56.083Z")
  test("Windows process birth identifies a live worker even when command line is unavailable") {
    assert(SceneProcessIdentity.matches(Some(start),Some(start),start.plusMillis(3),None,"job"))
    assert(SceneProcessIdentity.matches(None,Some(start),start.plusMillis(3),None,"job"))
  }
  test("a recycled process ID is rejected after restart, including old launch records") {
    assert(!SceneProcessIdentity.matches(Some(start),Some(start.plusSeconds(20)),start,None,"job"))
    assert(!SceneProcessIdentity.matches(None,Some(start.plusSeconds(20)),start,None,"job"))
    assert(!SceneProcessIdentity.matches(None,Some(start.minusSeconds(30)),start,None,"job"))
    assert(!SceneProcessIdentity.matches(None,None,start,None,"job"))
  }
