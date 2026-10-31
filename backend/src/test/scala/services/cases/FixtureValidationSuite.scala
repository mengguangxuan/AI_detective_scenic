package services.cases

import io.circe.{Json, parser}
import java.nio.file.{Files, Paths}
import munit.FunSuite
import services.cases.objects.*
import services.cases.utils.FixtureLoader
import services.system.objects.ErrorCode

class FixtureValidationSuite extends FunSuite:
  private val source = parser.parse(Files.readString(Paths.get("../docs/fixtures/browser_investigation.json"))).toOption.get
  private val original = FixtureLoader.decode(source.noSpaces).toOption.get

  private def rejected(record: CaseRevisionRecord, path: String): String =
    val result = FixtureLoader.validation(record)
    assert(result.isLeft)
    assert(!FixtureLoader.valid(record))
    val message = result.left.toOption.get
    assert(message.contains(path), message)
    message

  test("both authoring and stored content validate without changing their serialized meaning") {
    val files = Vector("minimal_investigation.json", "browser_investigation.json")
    files.foreach { name =>
      val first = FixtureLoader.decode(Files.readString(Paths.get("../docs/fixtures", name))).toOption.get
      assertEquals(FixtureLoader.validation(first), Right(()))
      val json = summon[io.circe.Encoder[CaseContent]].apply(first.content)
      val decoded = json.as[CaseContent].toOption.get
      assertEquals(decoded, first.content)
      assert(FixtureLoader.valid(first.copy(content = decoded)))
    }
  }

  test("review sections reject unknown clues and duplicate themes without publishing answer text") {
    val spec=original.content.conclusion.get
    val first=spec.reviewSections.head
    val unknown=spec.copy(reviewSections=Vector(first.copy(relatedClues=Set(ClueId("missing")))))
    rejected(original.copy(content=original.content.copy(conclusion=Some(unknown))),"investigation.conclusion")
    val duplicate=spec.copy(reviewSections=Vector(first,first))
    rejected(original.copy(content=original.content.copy(conclusion=Some(duplicate))),"investigation.conclusion")
  }

  test("node cycles and missing parents identify the offending scene entry") {
    val f = original.content
    val head = f.nodes.head
    rejected(original.copy(content = f.copy(nodes = f.nodes.updated(0, head.copy(parent = Some(head.id))))), "scene.nodes[0].parent")
    rejected(original.copy(content = f.copy(nodes = f.nodes.updated(0, head.copy(parent = Some(SceneNodeId("absent")))))), "scene.nodes[0].parent")
  }

  test("duplicate identifiers, unknown facts and wrong item reading routes identify their collections") {
    val f = original.content
    rejected(original.copy(content = f.copy(nodes = f.nodes :+ f.nodes.head)), "scene.nodes")
    rejected(original.copy(content = f.copy(clues = f.clues.updated(0, f.clues.head.copy(facts = Set(FactId("absent")))))), "investigation.clues[0].facts")
    val item = f.sceneObjects.find(_.entity == EntityId("clock")).get
    val index = f.sceneObjects.indexOf(item)
    rejected(original.copy(content = f.copy(sceneObjects = f.sceneObjects.updated(index, item.copy(readRoute = Some(RouteId("route_paper")))))), s"scene.objects[$index].readRoute")
  }

  test("follow-up diagnostics distinguish wrong ownership and a self-dependent critical route") {
    val f = original.content
    val first = f.followUps.head
    rejected(original.copy(content = f.copy(followUps = f.followUps.updated(0, first.copy(witness = WitnessId("mary"))))), "investigation.followUps[0].operation")
    rejected(original.copy(content = f.copy(followUps = f.followUps.updated(0, first.copy(requiredRoutes = Set(RouteId("route_redding_revised")))))), "investigation.followUps[0].requiredRoutes")
  }

  test("private stage prose is not included in authoring validation errors") {
    val changed = source.hcursor.downField("investigation").downField("followUps").downArray.downField("knowledge")
      .withFocus(_ => Json.arr(Json.fromString("PRIVATE_STAGE_SENTINEL" * 100))).top.get
    val error = FixtureLoader.decode(changed.noSpaces).left.toOption.get
    assertEquals(error.code, ErrorCode.InvalidValue)
    assert(error.publicMessage.contains("investigation.followUps[0]"), error.publicMessage)
    assert(!error.publicMessage.contains("PRIVATE_STAGE_SENTINEL"))
    assert(error.publicMessage.length < 500)
  }

  test("clock, report bypass, map and comparison errors expose their configuration paths") {
    val f = original.content
    rejected(original.copy(content = f.copy(clock = f.clock.map(_.copy(actionSeconds = 0)))), "investigation.clock.actionSeconds")
    val check = f.externalChecks.head
    val invalid = f.routes.head.copy(clues = Vector(check.resultClues.head))
    rejected(original.copy(content = f.copy(routes = f.routes.updated(0, invalid))), "investigation.externalChecks[0].resultClues")
    rejected(original.copy(content = f.copy(areaMap = f.areaMap.map(m => m.copy(roads = m.roads.updated(0, m.roads.head.copy(meters = 0)))))), "publicInfo.background.areaMap.roads[0].meters")
    val comparison = f.statementComparisons.head
    val row = comparison.rows.head.copy(initial = ClueId("clue_anne_statement"))
    rejected(original.copy(content = f.copy(statementComparisons = f.statementComparisons.updated(0, comparison.copy(rows = comparison.rows.updated(0, row))))), "investigation.statementComparisons[0].rows[0]")
  }

  test("isolated authoring item decoding retains its source array index") {
    val invalid = source.hcursor.downField("scene").downField("nodes").downArray.downField("transform").downField("scale")
      .withFocus(_ => Json.obj("x" -> Json.fromInt(2), "y" -> Json.fromInt(1), "z" -> Json.fromInt(1))).top.get
    val error = FixtureLoader.decode(invalid.noSpaces).left.toOption.get
    assertEquals(error.code, ErrorCode.InvalidValue)
    assert(error.publicMessage.contains("scene.nodes[0]"), error.publicMessage)
  }
