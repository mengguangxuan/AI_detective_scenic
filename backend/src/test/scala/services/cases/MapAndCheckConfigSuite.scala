package services.cases
import io.circe.{Json,parser}
import java.nio.file.{Files,Paths}
import munit.FunSuite
import services.cases.utils.FixtureLoader
class MapAndCheckConfigSuite extends FunSuite:
  private def source=parser.parse(Files.readString(Paths.get("../docs/fixtures/browser_investigation.json"))).toOption.get
  test("map roads must use known public world places and positive distances") {
    val valid=source
    assert(FixtureLoader.decode(valid.noSpaces).isRight)
    val missing=valid.hcursor.downField("publicInfo").downField("background").downField("areaMap").downField("roads").downArray.downField("to").withFocus(_ => Json.fromString("nonexistent")).top.get
    val distance=valid.hcursor.downField("publicInfo").downField("background").downField("areaMap").downField("roads").downArray.downField("meters").withFocus(_ => Json.fromInt(-1)).top.get
    assert(FixtureLoader.decode(missing.noSpaces).isLeft)
    assert(FixtureLoader.decode(distance.noSpaces).isLeft)
  }
  test("timed report results cannot also be obtained through an immediate investigation route") {
    val bypass=source.hcursor.downField("investigation").downField("routes").downArray.downField("grants").withFocus(_ => Json.arr(Json.fromString("clue_gun_verification"))).top.get
    assert(FixtureLoader.decode(bypass.noSpaces).isLeft)
    val invalidDelay=source.hcursor.downField("investigation").downField("externalChecks").downArray.downField("delaySeconds").withFocus(_ => Json.fromInt(0)).top.get
    assert(FixtureLoader.decode(invalidDelay.noSpaces).isLeft)
  }
