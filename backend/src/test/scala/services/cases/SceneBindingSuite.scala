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
