package services.games

import munit.FunSuite
import services.cases.objects.*
import services.cases.objects.scene.{FixtureNode, FixturePoint}
import services.games.utils.{FixtureRules, SceneAccessRules}
import services.system.objects.{Revision, SessionId, UserId, Vec3}

class SceneAccessSuite extends FunSuite:
  private val place = PlaceId("study")
  private val parent = FixtureNode(SceneNodeId("parent"),None,place,Vec3(3,0,0),true)
  private val child = FixtureNode(SceneNodeId("child"),Some(parent.id),place,Vec3(2,0,0),true)
  private val content = CaseContent(place,"study",0,"start",Vec3(0,0,0),Vector(parent,child),
    Vector.empty,Vector.empty,Vector.empty,Vector.empty,Vector.empty,Set.empty,Set.empty,Set.empty)
  private val state = FixtureRules.initial(CaseRevisionRecord(CaseId("case"),Revision(10),2,"case",content),SessionId("session"),UserId("user"))
  private val point = FixturePoint(PointId("point"),child.id,Vec3(1,0,0),6,"point")

  test("projection visibility and access both require a visible ancestor in the current place") {
    val hidden = state.copy(visibleNodes=Set(child.id))
    assert(!SceneAccessRules.positions(content,hidden).contains(child.id))
    assert(!SceneAccessRules.accessible(content,hidden,point))
    val elsewhere = state.copy(playerPlace=PlaceId("garden"))
    assert(SceneAccessRules.positions(content,elsewhere).isEmpty)
    assert(!SceneAccessRules.accessible(content,elsewhere,point))
  }

  test("reachability sums parent translations and local offsets with an inclusive radius") {
    assertEquals(SceneAccessRules.positions(content,state)(child.id),Vec3(5,0,0))
    assert(SceneAccessRules.accessible(content,state,point))
    assert(!SceneAccessRules.accessible(content,state,point.copy(radiusMeters=5.99)))
    assert(SceneAccessRules.accessible(content,state.copy(playerPosition=Vec3(6,0,0)),point.copy(radiusMeters=0)))
  }

  test("unknown nodes, missing ancestors and cycles never grant access") {
    assert(!SceneAccessRules.accessible(content,state,point.copy(node=SceneNodeId("unknown"))))
    val orphan = content.copy(nodes=Vector(child))
    assert(SceneAccessRules.positions(orphan,state).isEmpty)
    assert(!SceneAccessRules.accessible(orphan,state,point))
    val cyclic = content.copy(nodes=Vector(parent.copy(parent=Some(child.id)),child))
    assert(SceneAccessRules.positions(cyclic,state).isEmpty)
    assert(!SceneAccessRules.accessible(cyclic,state,point))
  }
