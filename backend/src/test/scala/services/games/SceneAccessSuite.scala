package services.games

import munit.FunSuite
import services.cases.objects.*
import services.cases.objects.scene.{FixtureNode, FixturePoint}
import services.games.utils.{FixtureRules, SceneAccessRules}
import services.system.objects.{SessionId, UserId, Vec3}

class SceneAccessSuite extends FunSuite:
  private val place = PlaceId("study")
  private val parent = FixtureNode(SceneNodeId("parent"),None,place,Vec3(3,0,0),true)
  private val child = FixtureNode(SceneNodeId("child"),Some(parent.id),place,Vec3(2,0,0),true)
  private val content = CaseContent(place,"study",0,"start",Vec3(0,0,0),Vector(parent,child),
    Vector.empty,Vector.empty,Vector.empty,Vector.empty,Vector.empty,Set.empty,Set.empty,Set.empty,sceneBundle="study")
  private val state = FixtureRules.initial(CaseRecord(CaseId("case"),2,"case",content),SessionId("session"),UserId("user"))
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

  test("scene state follows visible authored entities with arbitrary IDs and omits hidden or unregistered state") {
    import services.cases.objects.scene.{SceneObjectBinding,SceneObservation}
    import services.games.utils.InvestigationProjection
    import io.circe.syntax.*
    val outer=EntityId("cabinet_alpha")
    val inner=EntityId("panel_beta")
    val secret=EntityId("undisclosed_object")
    val authored=content.copy(sceneObjects=Vector(
      SceneObjectBinding(parent.id,outer,"柜体",None,None),SceneObjectBinding(child.id,inner,"内部面板",None,None)),
      observations=Vector(SceneObservation(parent.id,"柜体","公开外观"),SceneObservation(child.id,"内部面板","可见内部")))
    val record=CaseRecord(CaseId("case"),1,"场景绑定能力测试",authored)
    val opened=state.copy(opened=Set(outer,inner,secret))
    val full=InvestigationProjection.snapshot(record,opened,Vector.empty,Vector.empty)
    assertEquals(full.scene.openedEntities,Set(outer,inner))
    assert(!full.scene.asJson.noSpaces.contains(secret.value))
    assert(!full.scene.asJson.noSpaces.contains("drawerOpened"))
    assert(!full.scene.asJson.noSpaces.contains("windowOpened"))
    val hidden=InvestigationProjection.snapshot(record,opened.copy(visibleNodes=Set(parent.id)),Vector.empty,Vector.empty)
    assertEquals(hidden.scene.openedEntities,Set(outer))
    val away=InvestigationProjection.snapshot(record,opened.copy(playerPlace=PlaceId("elsewhere")),Vector.empty,Vector.empty)
    assertEquals(away.scene.openedEntities,Set.empty[EntityId])
  }
