package services.cases

import io.circe.{Json, parser}
import java.nio.file.{Files, Paths}
import munit.FunSuite
import services.cases.utils.FixtureLoader

class SceneBindingSuite extends FunSuite:
  private def source = parser.parse(Files.readString(Paths.get("../docs/fixtures/browser_investigation.json"))).toOption.get
  private def changeObjects(change: Vector[Json] => Vector[Json]): Json =
    source.hcursor.downField("scene").downField("objects").withFocus(json =>
      Json.fromValues(change(json.asArray.get.toVector))).top.get

  test("publishing rejects a model omitted from interaction registration") {
    assert(FixtureLoader.decode(source.noSpaces).isRight)
    assert(FixtureLoader.decode(changeObjects(_.dropRight(1)).noSpaces).isLeft)
  }
  test("publishing rejects duplicate items and reading through another item's route") {
    assert(FixtureLoader.decode(changeObjects(xs => xs :+ xs.head).noSpaces).isLeft)
    val mismatched = changeObjects(_.map(item =>
      if item.hcursor.get[String]("entity")==Right("clock") then
        item.mapObject(_.add("readRoute",Json.fromString("route_paper"))) else item))
    assert(FixtureLoader.decode(mismatched.noSpaces).isLeft)
  }

  private def observations(json: Json)(change: Vector[Json] => Vector[Json]): Json =
    json.hcursor.downField("scene").downField("observations").withFocus(value =>
      Json.fromValues(change(value.asArray.get.toVector))).top.get

  test("inherited observation labels compile to the same immutable revision as explicit labels") {
    val bindings = source.hcursor.downField("scene").get[Vector[Json]]("objects").toOption.get
    val labels = bindings.map(item => item.hcursor.get[String]("node").toOption.get ->
      item.hcursor.get[String]("label").toOption.get).toMap
    val explicit = observations(source)(_.map(item => item.mapObject(_.add("label",
      Json.fromString(labels(item.hcursor.get[String]("node").toOption.get))))))
    assertEquals(FixtureLoader.decode(source.noSpaces), FixtureLoader.decode(explicit.noSpaces))
  }

  test("an explicit observation label may differ from its interaction label") {
    val changed = observations(source)(items => items.updated(0,items.head.mapObject(_.add("label",Json.fromString("桌面外观")))))
    val compiled = FixtureLoader.decode(changed.noSpaces).toOption.get
    assertEquals(compiled.content.observations.head.label,"桌面外观")
  }

  test("unregistered observations require an explicit label and reject null labels") {
    val unregistered = observations(source)(items => items.updated(0,items.head.mapObject(_.add("node",Json.fromString("unregistered")))))
    val missing = FixtureLoader.decode(unregistered.noSpaces).swap.toOption.get
    assert(missing.getMessage.contains("scene.observations[0]") && missing.getMessage.contains("label"))
    val nullLabel = observations(source)(items => items.updated(0,items.head.mapObject(_.add("label",Json.Null))))
    assert(FixtureLoader.decode(nullLabel.noSpaces).isLeft)
  }
