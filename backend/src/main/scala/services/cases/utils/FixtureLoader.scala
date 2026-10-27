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
    yield FixturePoint(id, node, local, radius)

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
      _ <- check(method == "Fixed" && condition == "OperationDone", "Unsupported route protocol", c)
      prerequisite <- c.downField("condition").get[OperationId]("operation")
      clues <- c.get[Vector[ClueId]]("grants")
      _ <- check(clues.nonEmpty, "Empty grants", c)
      _ <- empty(c, "reports")
    yield FixtureRoute(id, point, prerequisite, clues)

  private def operation(raw: Json): Decoder.Result[FixtureOperation] =
    val c = raw.hcursor
    for
      id <- c.get[OperationId]("id")
      point <- c.get[PointId]("point")
      kind <- c.downField("condition").get[String]("kind")
      conditions <- c.downField("condition").get[Vector[Json]]("items")
      _ <- check(kind == "All" && conditions.isEmpty, "Only unconditional fixture operations supported", c)
      rawEffects <- c.get[Vector[Json]]("effects")
      effects <- rawEffects.traverse { raw =>
        for
          kind <- raw.hcursor.get[String]("kind")
          _ <- check(kind == "World", "Only world effects supported", c)
          effect <- raw.hcursor.get[FixtureEffect]("effect")
        yield effect
      }
      _ <- check(effects.nonEmpty, "Empty effects", c)
    yield FixtureOperation(id, point, effects)

  private[cases] def decode(raw: String): Either[GameError, InvestigationFixture] =
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
      _ <- empty(background.hcursor, "persons", "openingReports", "openingMaterials")
      places <- world.hcursor.get[Vector[Json]]("places")
      _ <- check(places.size == 1, "Only one-room fixture supported", c)
      place <- places.head.hcursor.get[PlaceId]("id")
      caseId <- c.get[CaseId]("id")
      revision <- c.get[Revision]("revision")
      reducer <- c.get[ReducerVersion]("reducerVersion")
      rawNodes <- scene.hcursor.get[Vector[Json]]("nodes")
      nodes <- rawNodes.traverse(node)
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
      entities <- world.hcursor.get[Vector[Json]]("entities")
      entityIds <- entities.traverse(_.hcursor.get[EntityId]("id"))
      _ <- check(opened.subsetOf(entityIds.toSet) && unlocked.subsetOf(entityIds.toSet), "Unknown initial entity", c)
      _ <- check(operations.flatMap(_.effects).forall {
        case FixtureEffect.Open(entity) => entityIds.contains(entity)
        case _ => true
      }, "Missing effect entity", c)
      fixture = InvestigationFixture(caseId, revision, reducer, place, position, nodes, points, facts, clues, routes, operations, opened, unlocked, common)
      _ <- check(valid(fixture), "Invalid fixture references / node hierarchy", c)
    yield fixture).left.map(_ => GameError(ErrorCode.InvalidValue, "最小调查数据格式或引用不合法。"))

  private def valid(f: InvestigationFixture): Boolean =
    def finite(p: Vec3) = Vector(p.x, p.y, p.z).forall(_.isFinite)
    def unique[A](values: Vector[A]) = values.distinct.size == values.size
    val nodes = f.nodes.map(n => n.id -> n).toMap
    def validChain(id: SceneNodeId, seen: Set[SceneNodeId]): Boolean =
      !seen(id) && nodes.get(id).exists(n => n.place == f.place && n.parent.forall(validChain(_, seen + id)))
    f.revision.value > 0 && f.reducerVersion.value == "fixture-v1" && finite(f.spawnPosition) &&
      unique(f.nodes.map(_.id)) && unique(f.points.map(_.id)) && unique(f.facts.map(_.id)) &&
      unique(f.clues.map(_.id)) && unique(f.routes.map(_.id)) && unique(f.operations.map(_.id)) &&
      f.nodes.forall(n => finite(n.position) && validChain(n.id, Set.empty)) &&
      f.points.forall(p => nodes.contains(p.node) && finite(p.localPoint) && p.radiusMeters.isFinite && p.radiusMeters > 0) &&
      f.clues.forall(c => c.facts.subsetOf(f.facts.map(_.id).toSet)) &&
      f.commonFacts.subsetOf(f.facts.map(_.id).toSet) &&
      f.routes.forall(r => f.points.exists(_.id == r.point) && f.operations.exists(_.id == r.prerequisite) && r.clues.forall(id => f.clues.exists(_.id == id))) &&
      f.operations.forall(o => f.points.exists(_.id == o.point) && o.effects.forall {
        case FixtureEffect.SetNodeVisible(node, _) => nodes.contains(node)
        case _ => true
      })
