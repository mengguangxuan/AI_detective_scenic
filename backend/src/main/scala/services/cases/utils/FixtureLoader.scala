package services.cases.utils

import cats.syntax.all.*
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import io.circe.parser.parse
import services.cases.objects.*
import services.system.objects.*

private[cases] object FixtureLoader:
  private def check(ok: Boolean, message: String, cursor: HCursor): Decoder.Result[Unit] =
    Either.cond(ok, (), DecodingFailure(message, cursor.history))

  private def empty(cursor: HCursor, names: String*): Decoder.Result[Unit] =
    names.toVector.traverse_(name => cursor.get[Vector[Json]](name).flatMap(xs => check(xs.isEmpty, s"Unsupported nonempty $name", cursor)))

  private def node(raw: Json): Decoder.Result[FixtureNode] =
    val c = raw.hcursor
    for
      id <- c.get[SceneNodeId]("id")
      parent <- c.get[Option[SceneNodeId]]("parent")
      place <- c.get[PlaceId]("place")
      visible <- c.get[Boolean]("initiallyVisible")
      position <- c.downField("transform").get[Vec3]("position")
      rotation <- c.downField("transform").get[Map[String, Double]]("rotation")
      scale <- c.downField("transform").get[Vec3]("scale")
      _ <- check(rotation == Map("xRad" -> 0.0, "yRad" -> 0.0, "zRad" -> 0.0) && scale == Vec3(1, 1, 1), "This fixture profile supports translation-only nodes", c)
    yield FixtureNode(id, parent, place, position, visible)

  private def point(raw: Json): Decoder.Result[FixturePoint] =
    val c = raw.hcursor
    for
      id <- c.get[PointId]("id")
      kind <- c.downField("entry").get[String]("kind")
      _ <- check(kind == "OnNode", "Only OnNode entries supported", c)
      node <- c.downField("entry").get[SceneNodeId]("node")
      local <- c.downField("entry").get[Vec3]("localPoint")
      radius <- c.downField("entry").get[Double]("radiusMeters")
    yield FixturePoint(id, node, local, radius, c.get[String]("label").getOrElse(id.value))

  private def fact(raw: Json): Decoder.Result[FixtureFact] =
    val c = raw.hcursor
    for
      id <- c.get[FactId]("id")
      _ <- empty(c, "requiresOccurred")
      kind <- c.downField("value").get[String]("kind")
      _ <- check(kind == "AuthoredDetail", "Only authored facts supported", c)
      detail <- c.downField("value").get[Text]("detail")
    yield FixtureFact(id, detail)

  private def clue(raw: Json): Decoder.Result[FixtureClue] =
    val c = raw.hcursor
    for
      id <- c.get[ClueId]("id")
      title <- c.get[Text]("title")
      text <- c.downField("result").get[Text]("text")
      facts <- c.get[Set[FactId]]("facts")
      _ <- empty(c, "relatedClaims", "suggests")
      media <- c.downField("result").get[Vector[Json]]("media")
      _ <- check(media.isEmpty && facts.nonEmpty, "Only nonempty fact clues without media supported", c)
    yield FixtureClue(id, title, text, facts)

  private def route(raw: Json): Decoder.Result[FixtureRoute] =
    val c = raw.hcursor
    for
      id <- c.get[RouteId]("id")
      point <- c.get[PointId]("point")
      method <- c.downField("method").get[String]("kind")
      condition <- c.downField("condition").get[String]("kind")
      _ <- check(method == "Fixed", "Unsupported route method", c)
      prerequisite <- condition match
        case "OperationDone" => c.downField("condition").get[OperationId]("operation").map(Some(_))
        case "Always" | "CluesKnown" => Right(None)
        case _ => Left(DecodingFailure("Unsupported route condition",c.history))
      required <- condition match
        case "CluesKnown" => c.downField("condition").get[Set[ClueId]]("clues").flatMap(xs =>
          check(xs.nonEmpty,"Empty clue prerequisite",c).as(xs))
        case _ => Right(Set.empty[ClueId])
      clues <- c.get[Vector[ClueId]]("grants")
      presented <- c.get[Option[Set[ClueId]]]("presentedClues").map(_.getOrElse(Set.empty))
      _ <- check(clues.nonEmpty, "Empty grants", c)
      _ <- empty(c, "reports")
    yield FixtureRoute(id, point, prerequisite, clues, c.downField("method").get[String]("label").getOrElse(id.value), required, presented)

  private def operation(raw: Json): Decoder.Result[FixtureOperation] =
    val c = raw.hcursor
    for
      id <- c.get[OperationId]("id")
      point <- c.get[PointId]("point")
      condition <- c.get[Json]("condition")
      kind <- condition.hcursor.get[String]("kind")
      requirement <- kind match
        case "All" => condition.hcursor.get[Vector[Json]]("items").flatMap(items => check(items.isEmpty,"Unsupported operation condition",c)).map(_ => Option.empty[EntityOpenRequirement])
        case "EntityOpen" => (condition.hcursor.get[EntityId]("entity"),condition.hcursor.get[Boolean]("opened"))
          .mapN((entity,opened) => Option(EntityOpenRequirement(entity,opened)))
        case "CluesKnown" => Right(Option.empty[EntityOpenRequirement])
        case _ => Left(DecodingFailure("Unsupported operation condition",c.history))
      required <- if kind == "CluesKnown" then condition.hcursor.get[Set[ClueId]]("clues").flatMap(xs =>
        check(xs.nonEmpty,"Empty operation clue prerequisite",c).as(xs)) else Right(Set.empty[ClueId])
      repeatable <- c.get[Option[Boolean]]("repeatable")
      rawEffects <- c.get[Vector[Json]]("effects")
      effects <- rawEffects.traverse { raw =>
        for
          kind <- raw.hcursor.get[String]("kind")
          _ <- check(kind == "World", "Only world effects supported", c)
          effect <- raw.hcursor.get[FixtureEffect]("effect")
        yield effect
      }
      _ <- check(effects.nonEmpty, "Empty effects", c)
    yield FixtureOperation(id, point, effects, c.get[String]("label").getOrElse(id.value),repeatable.getOrElse(false),requirement,required)

  private[cases] def decode(raw: String): Either[GameError, CaseRevisionRecord] =
    (for
      json <- parse(raw)
      c = json.hcursor
      version <- c.downField("schemaVersion").get[Int]("value")
      _ <- check(version == 2, "Unsupported schema version", c)
      kind <- c.downField("investigation").downField("assessment").get[String]("kind")
      _ <- check(kind == "InvestigationOnly", "Only InvestigationOnly supported", c)
      world <- c.get[Json]("world")
      truth <- c.get[Json]("truth")
      scene <- c.get[Json]("scene")
      investigation <- c.get[Json]("investigation")
      _ <- empty(world.hcursor, "connections", "environments", "relationships", "scenes")
      _ <- empty(truth.hcursor, "persons", "privateContexts", "motives", "events", "incidents", "claims")
      _ <- empty(scene.hcursor, "requirementBindings", "cues")
      _ <- empty(investigation.hcursor, "questions", "options", "rules", "scheduled")
      publicInfo <- c.get[Json]("publicInfo")
      _ <- empty(publicInfo.hcursor, "reports")
      background <- c.downField("publicInfo").get[Json]("background")
      _ <- empty(background.hcursor, "openingReports", "openingMaterials")
      publicPersons <- background.hcursor.get[Vector[CasePersonBrief]]("persons")
      places <- world.hcursor.get[Vector[Json]]("places")
      _ <- check(places.nonEmpty, "Missing places", c)
      place <- c.downField("investigation").downField("initial").get[PlaceId]("spawnPlace")
      playable <- places.find(_.hcursor.get[PlaceId]("id").contains(place)).toRight(DecodingFailure("Unknown playable place",c.history))
      caseId <- c.get[CaseId]("id")
      revision <- c.get[Revision]("revision")
      title <- c.get[String]("title")
      placeName <- playable.hcursor.get[String]("name")
      time <- c.downField("initialStoryTime").get[Long]("seconds")
      origin <- c.get[String]("timeOriginLabel")
      rawNodes <- scene.hcursor.get[Vector[Json]]("nodes")
      nodes <- rawNodes.traverse(node)
      observations <- scene.hcursor.get[Option[Vector[SceneObservation]]]("observations").map(_.getOrElse(Vector.empty))
      sceneObjects <- scene.hcursor.get[Option[Vector[SceneObjectBinding]]]("objects").map(_.getOrElse(Vector.empty))
      represented <- rawNodes.traverse(raw => (raw.hcursor.get[SceneNodeId]("id"), raw.hcursor.get[Option[EntityId]]("represents")).tupled)
      _ <- check(sceneObjects.isEmpty || represented.collect { case (node, Some(entity)) => node -> entity }.toSet ==
        sceneObjects.map(o => o.node -> o.entity).toSet, "Scene object registration does not cover the represented models", c)
      rawPoints <- scene.hcursor.get[Vector[Json]]("points")
      points <- rawPoints.traverse(point)
      rawFacts <- truth.hcursor.get[Vector[Json]]("facts")
      facts <- rawFacts.traverse(fact)
      rawClues <- investigation.hcursor.get[Vector[Json]]("clues")
      clues <- rawClues.traverse(clue)
      rawRoutes <- investigation.hcursor.get[Vector[Json]]("routes")
      routes <- rawRoutes.traverse(route)
      rawOperations <- investigation.hcursor.get[Vector[Json]]("operations")
      operations <- rawOperations.traverse(operation)
      initial <- investigation.hcursor.get[Json]("initial")
      spawn <- initial.hcursor.get[PlaceId]("spawnPlace")
      _ <- check(spawn == place, "Spawn place mismatch", c)
      position <- initial.hcursor.get[Vec3]("spawnPosition")
      opened <- initial.hcursor.get[Set[EntityId]]("opened")
      unlocked <- initial.hcursor.get[Set[EntityId]]("unlocked")
      _ <- empty(initial.hcursor, "npcs")
      occurred <- initial.hcursor.get[Map[String, Json]]("occurredEvents")
      _ <- check(occurred.isEmpty, "No scheduled history in this profile", c)
      common <- background.hcursor.get[Set[FactId]]("commonKnowledge")
      stage <- investigation.hcursor.get[Option[InvestigationStage]]("stage")
      witnesses <- investigation.hcursor.get[Option[Vector[WitnessProfile]]]("witnesses").map(_.getOrElse(Vector.empty))
      police <- investigation.hcursor.get[Option[PoliceContact]]("police")
      areaMap <- background.hcursor.get[Option[AreaMap]]("areaMap")
      clock <- investigation.hcursor.get[Option[InvestigationClock]]("clock")
      checks <- investigation.hcursor.get[Option[Vector[ExternalCheckSpec]]]("externalChecks").map(_.getOrElse(Vector.empty))
      placeIds <- places.traverse(_.hcursor.get[PlaceId]("id"))
      _ <- check(placeIds.distinct.size == placeIds.size && areaMap.forall(_.locations.forall(l => placeIds.contains(l.id))),"Unknown map place",c)
      entities <- world.hcursor.get[Vector[Json]]("entities")
      entityIds <- entities.traverse(_.hcursor.get[EntityId]("id"))
      _ <- check(sceneObjects.forall(o => entityIds.contains(o.entity)), "Unknown scene object entity", c)
      _ <- check(opened.subsetOf(entityIds.toSet) && unlocked.subsetOf(entityIds.toSet), "Unknown initial entity", c)
      _ <- check(operations.flatMap(_.effects).forall {
        case FixtureEffect.Open(entity) => entityIds.contains(entity)
        case FixtureEffect.SetOpened(entity, _) => entityIds.contains(entity)
        case _ => true
      }, "Missing effect entity", c)
      _ <- check(operations.forall(_.openedRequirement.forall(r => entityIds.contains(r.entity))),"Unknown operation condition entity",c)
      content = CaseContent(place, placeName, time, origin, position, nodes, points, facts, clues, routes, operations, opened, unlocked, common, observations, stage, sceneObjects, witnesses, police, publicPersons, areaMap, clock, checks)
      record = CaseRevisionRecord(caseId, revision, 1, title, content)
      _ <- check(valid(record), "Invalid fixture references / node hierarchy", c)
    yield record).left.map(error => GameError(ErrorCode.InvalidValue, s"案件数据不合法：${error.getMessage}"))

  private[cases] def valid(record: CaseRevisionRecord): Boolean =
    val f = record.content
    def finite(p: Vec3) = Vector(p.x, p.y, p.z).forall(_.isFinite)
    def unique[A](values: Vector[A]) = values.distinct.size == values.size
    val nodes = f.nodes.map(n => n.id -> n).toMap
    def validChain(id: SceneNodeId, seen: Set[SceneNodeId]): Boolean =
      !seen(id) && nodes.get(id).exists(n => n.place == f.place && n.parent.forall(validChain(_, seen + id)))
    record.revision.value > 0 && record.contentSchemaVersion == 1 && record.title.trim.nonEmpty && f.placeName.trim.nonEmpty && f.timeOriginLabel.trim.nonEmpty && finite(f.spawnPosition) &&
      f.clock.forall(c => c.startMinutesOfDay >= 0 && c.startMinutesOfDay < 1440 && c.actionSeconds >= 60 && c.actionSeconds <= 600 && c.actionSeconds % 60 == 0) &&
      (f.externalChecks.isEmpty || (f.clock.nonEmpty && f.police.nonEmpty)) && unique(f.externalChecks.map(_.id)) &&
      f.externalChecks.forall(c => c.title.trim.nonEmpty && c.requestLabel.trim.nonEmpty && c.delaySeconds >= 60 && c.delaySeconds <= 7200 && c.delaySeconds % 60 == 0 &&
        f.police.exists(_.point == c.point) && c.requiredClues.subsetOf(f.clues.map(_.id).toSet) && c.resultClues.nonEmpty &&
        c.resultClues.subsetOf(f.clues.map(_.id).toSet) && c.requiredClues.intersect(c.resultClues).isEmpty &&
        !f.routes.exists(r => r.clues.exists(c.resultClues))) &&
      f.areaMap.forall(m => m.note.trim.nonEmpty && m.locations.nonEmpty && unique(m.locations.map(_.id)) && unique(m.roads.map(_.id)) &&
        m.locations.forall(l => l.name.trim.nonEmpty && l.description.trim.nonEmpty && l.x.isFinite && l.y.isFinite && l.x >= 40 && l.x <= 1160 && l.y >= 40 && l.y <= 660) &&
        m.roads.forall(r => r.id.matches("[a-z][a-z0-9_]{0,63}") && r.from != r.to && m.locations.exists(_.id == r.from) && m.locations.exists(_.id == r.to) && r.meters > 0 && r.meters <= 10000 && r.label.trim.nonEmpty)) &&
      unique(f.nodes.map(_.id)) && unique(f.points.map(_.id)) && unique(f.facts.map(_.id)) &&
      unique(f.clues.map(_.id)) && unique(f.routes.map(_.id)) && unique(f.operations.map(_.id)) &&
      f.nodes.forall(n => finite(n.position) && validChain(n.id, Set.empty)) &&
      f.points.forall(p => nodes.contains(p.node) && finite(p.localPoint) && p.radiusMeters.isFinite && p.radiusMeters > 0) &&
      f.clues.forall(c => c.facts.subsetOf(f.facts.map(_.id).toSet)) &&
      f.commonFacts.subsetOf(f.facts.map(_.id).toSet) &&
      unique(f.publicPersons.map(_.id)) && f.publicPersons.forall(p => p.id.value.matches("[a-z][a-z0-9_]{0,63}") &&
        Vector(p.name,p.identity,p.victimRelation,p.caseRelation).forall(_.trim.nonEmpty) &&
        p.witness.forall(id => f.witnesses.exists(_.id == id) || (id == WitnessId("melchett") && f.police.nonEmpty))) &&
      f.police.forall(p => p.name.trim.nonEmpty && p.background.trim.nonEmpty &&
        f.points.exists(_.id == p.point) && !f.witnesses.exists(_.point == p.point)) &&
      unique(f.witnesses.map(_.id)) && unique(f.witnesses.map(_.point)) && f.witnesses.forall(w =>
        w.id.value.matches("[a-z][a-z0-9_]{0,63}") && w.name.trim.nonEmpty && w.background.trim.nonEmpty && w.voice.trim.nonEmpty &&
        f.points.exists(_.id == w.point) && w.answers.nonEmpty && unique(w.answers.map(_.id)) &&
        w.answers.forall(a => a.id.value.matches("[a-z][a-z0-9_]{0,99}") && a.text.trim.nonEmpty && a.text.length <= 1500 &&
          a.requiredEvidence.subsetOf(f.clues.map(_.id).toSet) && a.routes.distinct.size == a.routes.size &&
          a.routes.forall(id => f.routes.exists(r => r.id == id && r.point == w.point)))) &&
      f.routes.forall(r => f.points.exists(_.id == r.point) && r.prerequisite.forall(op => f.operations.exists(_.id == op)) &&
        r.clues.forall(id => f.clues.exists(_.id == id)) && r.requiredClues.subsetOf(f.clues.map(_.id).toSet) &&
        r.requiredClues.intersect(r.clues.toSet).isEmpty && r.presentedClues.subsetOf(r.requiredClues)) &&
      f.stage.forall(s => s.introduction.trim.nonEmpty && s.objective.trim.nonEmpty && f.clues.exists(_.id == s.completionClue) &&
        s.comparison.forall(c => f.points.exists(_.id == c.point) && !f.routes.exists(_.id == c.route) && c.requiredClues.nonEmpty && c.evidenceClues.nonEmpty &&
          c.evidenceClues.subsetOf(c.requiredClues) && c.requiredClues.subsetOf(f.clues.map(_.id).toSet) &&
          !c.requiredClues(s.completionClue) && c.convertedTimeMinutes >= 0 && c.convertedTimeMinutes < 1440 &&
          !f.routes.exists(_.clues.contains(s.completionClue)))) &&
      unique(f.sceneObjects.map(_.node)) && unique(f.sceneObjects.map(_.entity)) && f.sceneObjects.forall(o =>
        nodes.contains(o.node) && o.label.trim.nonEmpty && o.point.forall(p => f.points.exists(x => x.id == p && x.node == o.node)) &&
        o.readRoute.forall(r => f.routes.exists(x => x.id == r && o.point.contains(x.point))) &&
        (o.point.nonEmpty || f.observations.exists(_.node == o.node))) &&
      unique(f.observations.map(_.node)) && f.observations.forall(o => nodes.contains(o.node) && o.label.trim.nonEmpty && o.description.trim.nonEmpty) &&
      f.operations.forall(o => f.points.exists(_.id == o.point) && o.requiredClues.subsetOf(f.clues.map(_.id).toSet) && o.effects.forall {
        case FixtureEffect.SetNodeVisible(node, _) => nodes.contains(node)
        case _ => true
      })
