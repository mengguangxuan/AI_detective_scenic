package services.cases
import io.circe.{Json,parser}
import java.nio.file.{Files,Paths}
import munit.FunSuite
import services.cases.utils.FixtureLoader
class FollowUpConfigSuite extends FunSuite:
  private def source=parser.parse(Files.readString(Paths.get("../docs/fixtures/browser_investigation.json"))).toOption.get
  test("follow-up operations must belong to the stated witness and reference reachable preparation questions") {
    assert(FixtureLoader.decode(source.noSpaces).isRight)
    val wrong=source.hcursor.downField("investigation").downField("followUps").downArray.downField("witness").withFocus(_ => Json.fromString("mary")).top.get
    assert(FixtureLoader.decode(wrong.noSpaces).isLeft)
    val future=source.hcursor.downField("investigation").downField("followUps").downArray.downField("requiredRoutes").withFocus(_ => Json.arr(Json.fromString("route_redding_revised"))).top.get
    assert(FixtureLoader.decode(future.noSpaces).isLeft)
  }
  test("statement comparisons require known clues of the same witness and a gated revised version") {
    val wrong=source.hcursor.downField("investigation").downField("statementComparisons").downArray.downField("rows").downArray.downField("initial").withFocus(_ => Json.fromString("clue_anne_statement")).top.get
    assert(FixtureLoader.decode(wrong.noSpaces).isLeft)
    val early=source.hcursor.downField("investigation").downField("statementComparisons").downArray.downField("rows").downArray.downField("revised").withFocus(_ => Json.fromString("clue_redding_gun")).top.get
    assert(FixtureLoader.decode(early.noSpaces).isLeft)
  }
