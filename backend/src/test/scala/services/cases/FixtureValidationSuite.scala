package services.cases

import io.circe.{Json, parser}
import java.nio.file.{Files, Paths}
import munit.FunSuite
import services.cases.objects.*
import services.cases.utils.FixtureLoader
import services.system.objects.ErrorCode

class FixtureValidationSuite extends FunSuite:
  private val source = parser.parse(Files.readString(Paths.get("../docs/fixtures/investigation_base.json"))).toOption.get
  private val original = FixtureLoader.decode(source.noSpaces).toOption.get

  private def rejected(record: CaseRecord, path: String): String =
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

  test("multi-scene movement rejects unregistered stops, renderer versions and cross-place parent chains") {
    val record=FixtureLoader.decode(Files.readString(Paths.get("../docs/fixtures/browser_investigation.json"))).toOption.get
    val content=record.content
    assertEquals(content.sceneLocations.size,2)
    val unsupported=content.sceneLocations.map(l => if l.place == PlaceId("woods") then l.copy(bundleId="unregistered-scene") else l)
    rejected(record.copy(content=content.copy(sceneLocations=unsupported)),"scene.locations")
    val moves=content.operations.map(o => if o.id == OperationId("enter_woodland") then o.copy(effects=Vector(
      services.cases.objects.investigation.FixtureEffect.Relocate(PlaceId("woods"),services.system.objects.Vec3(999,0,0)))) else o)
    rejected(record.copy(content=content.copy(operations=moves)),"position")
    val crossing=content.nodes.map(n => if n.id == SceneNodeId("node_woodland_fragment") then n.copy(parent=Some(SceneNodeId("node_desk"))) else n)
    rejected(record.copy(content=content.copy(nodes=crossing)),"parent")
    val old=summon[io.circe.Encoder[CaseContent]].apply(original.content)
    assert(!old.hcursor.downField("sceneLocations").succeeded)
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

  test("interview prompt policy versions are explicit and legacy content retains its serialized shape") {
    val json=summon[io.circe.Encoder[CaseContent]].apply(original.content)
    assert(!json.hcursor.downField("interviewPromptVersion").succeeded)
    assertEquals(json.as[CaseContent].toOption.get.interviewPromptVersion,1)
    val updated=original.copy(content=original.content.copy(interviewPromptVersion=2))
    assertEquals(FixtureLoader.validation(updated),Right(()))
    rejected(original.copy(content=original.content.copy(interviewPromptVersion=99)),"investigation.interviewPromptVersion")
  }

  test("semantic assessment does not require five placeholder coverage groups") {
    val content=original.content.copy(conclusion=original.content.conclusion.map(_.copy(evidenceGroups=Vector.empty)))
    assert(FixtureLoader.valid(original.copy(content=content)))
    val coverageOnly=content.copy(conclusion=content.conclusion.map(_.copy(evaluation=None)))
    assert(!FixtureLoader.valid(original.copy(content=coverageOnly)))
  }

  test("clock weekdays and calendar origins are explicit, preserved and checked against the opening story time") {
    val clock=original.content.clock.get
    for day <- Vector(0,8) do
      rejected(original.copy(content=original.content.copy(clock=Some(clock.copy(startWeekday=Some(day))))),"investigation.clock.startWeekday")
    val dated=source.mapObject(_.add("calendarOrigin",Json.fromString("1930-11-03T09:00:00+01:00")))
    val decoded=FixtureLoader.decode(dated.noSpaces).toOption.get
    assertEquals(decoded.content.calendarOrigin.map(_.toString),Some("1930-11-03T09:00+01:00"))
    val json=summon[io.circe.Encoder[CaseContent]].apply(decoded.content)
    assertEquals(json.as[CaseContent].toOption.get,decoded.content)
    rejected(decoded.copy(content=decoded.content.copy(clock=Some(clock.copy(startMinutesOfDay=480)))),"calendarOrigin")
    val mismatchedWeekday=decoded.content.copy(clock=Some(clock.copy(startWeekday=Some(2))))
    rejected(decoded.copy(content=mismatchedWeekday),"calendarOrigin")
    assert(FixtureLoader.decode(source.mapObject(_.add("calendarOrigin",Json.fromString("not-a-date"))).noSpaces).isLeft)
  }

  test("authored scenes reject unknown primitives, unregistered instances and invalid room references before publication") {
    val raw=parser.parse(Files.readString(Paths.get("../docs/fixtures/speckled_band_daytime.json"))).toOption.get
    val record=FixtureLoader.decode(raw.noSpaces).toOption.get
    val blueprint=record.content.sceneBlueprint.get
    rejected(record.copy(content=record.content.copy(sceneBlueprint=Some(blueprint.copy(rooms=blueprint.rooms.tail)))),"scene.blueprint.rooms")
    val changed=blueprint.visuals.head.copy(node=SceneNodeId("unknown"))
    rejected(record.copy(content=record.content.copy(sceneBlueprint=Some(blueprint.copy(visuals=changed+:blueprint.visuals.tail)))),"scene.blueprint.visuals")
    val visual=blueprint.visuals.head
    val invalid=visual.copy(parts=visual.parts.head.copy(color="url(untrusted)")+:visual.parts.tail)
    rejected(record.copy(content=record.content.copy(sceneBlueprint=Some(blueprint.copy(visuals=invalid+:blueprint.visuals.tail)))),"scene.blueprint.visuals")
    val shape=raw.hcursor.downField("scene").downField("blueprint").downField("visuals").downArray.downField("parts").downArray.downField("shape").withFocus(_ => Json.fromString("ExecuteScript")).top.get
    assert(FixtureLoader.decode(shape.noSpaces).isLeft)
    rejected(record.copy(content=record.content.copy(clock=record.content.clock.map(_.copy(operationDurations=Vector(services.cases.objects.investigation.OperationDuration(OperationId("missing"),60)))))),"investigation.clock.operationDurations")
  }
