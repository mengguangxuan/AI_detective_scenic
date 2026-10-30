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

  private def decodeEntries[A](values: Vector[Json], path: String)(decode: Json => Decoder.Result[A]): Decoder.Result[Vector[A]] =
    values.zipWithIndex.traverse { case (raw, index) =>
      decode(raw).left.map(error => DecodingFailure(s"$path[$index]: ${error.message}", error.history))
    }

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
      disabledAfter <- c.get[Option[OperationId]]("disabledAfter")
      clues <- c.get[Vector[ClueId]]("grants")
      presented <- c.get[Option[Set[ClueId]]]("presentedClues").map(_.getOrElse(Set.empty))
      _ <- check(clues.nonEmpty, "Empty grants", c)
      _ <- empty(c, "reports")
    yield FixtureRoute(id, point, prerequisite, clues, c.downField("method").get[String]("label").getOrElse(id.value), required, presented, disabledAfter)

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
      nodes <- decodeEntries(rawNodes, "scene.nodes")(node)
      observations <- scene.hcursor.get[Option[Vector[SceneObservation]]]("observations").map(_.getOrElse(Vector.empty))
      sceneObjects <- scene.hcursor.get[Option[Vector[SceneObjectBinding]]]("objects").map(_.getOrElse(Vector.empty))
      represented <- rawNodes.traverse(raw => (raw.hcursor.get[SceneNodeId]("id"), raw.hcursor.get[Option[EntityId]]("represents")).tupled)
      _ <- check(sceneObjects.isEmpty || represented.collect { case (node, Some(entity)) => node -> entity }.toSet ==
        sceneObjects.map(o => o.node -> o.entity).toSet, "scene.objects：登记未覆盖模型或与模型实体不一致", c)
      rawPoints <- scene.hcursor.get[Vector[Json]]("points")
      points <- decodeEntries(rawPoints, "scene.points")(point)
      rawFacts <- truth.hcursor.get[Vector[Json]]("facts")
      facts <- decodeEntries(rawFacts, "truth.facts")(fact)
      rawClues <- investigation.hcursor.get[Vector[Json]]("clues")
      clues <- decodeEntries(rawClues, "investigation.clues")(clue)
      rawRoutes <- investigation.hcursor.get[Vector[Json]]("routes")
      routes <- decodeEntries(rawRoutes, "investigation.routes")(route)
      rawOperations <- investigation.hcursor.get[Vector[Json]]("operations")
      operations <- decodeEntries(rawOperations, "investigation.operations")(operation)
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
      followUps <- investigation.hcursor.get[Option[Vector[WitnessFollowUpSpec]]]("followUps").map(_.getOrElse(Vector.empty))
      comparisons <- investigation.hcursor.get[Option[Vector[StatementComparisonSpec]]]("statementComparisons").map(_.getOrElse(Vector.empty))
      placeIds <- places.traverse(_.hcursor.get[PlaceId]("id"))
      _ <- check(placeIds.distinct.size == placeIds.size && areaMap.forall(_.locations.forall(l => placeIds.contains(l.id))),"publicInfo.background.areaMap.locations：地点不在world.places中或world.places有重复编号",c)
      entities <- world.hcursor.get[Vector[Json]]("entities")
      entityIds <- entities.traverse(_.hcursor.get[EntityId]("id"))
      _ <- check(sceneObjects.forall(o => entityIds.contains(o.entity)), "scene.objects.entity：实体不在world.entities中", c)
      _ <- check(opened.subsetOf(entityIds.toSet) && unlocked.subsetOf(entityIds.toSet), "investigation.initial：初始开关引用了未知实体", c)
      _ <- check(operations.flatMap(_.effects).forall {
        case FixtureEffect.Open(entity) => entityIds.contains(entity)
        case FixtureEffect.SetOpened(entity, _) => entityIds.contains(entity)
        case _ => true
      }, "investigation.operations.effects：效果引用了未知实体", c)
      _ <- check(operations.forall(_.openedRequirement.forall(r => entityIds.contains(r.entity))),"investigation.operations.openedRequirement：前提引用了未知实体",c)
      content = CaseContent(place, placeName, time, origin, position, nodes, points, facts, clues, routes, operations, opened, unlocked, common, observations, stage, sceneObjects, witnesses, police, publicPersons, areaMap, clock, checks, followUps, comparisons)
      record = CaseRevisionRecord(caseId, revision, 1, title, content)
      _ <- validation(record).left.map(message => DecodingFailure(message,c.history))
    yield record).left.map(error => GameError(ErrorCode.InvalidValue, s"案件数据不合法：${error.getMessage}"))

  private def rule(ok: Boolean, path: String, message: String): Either[String, Unit] =
    Either.cond(ok, (), s"$path：$message")

  private def entries[A](values: Vector[A], path: String)(validate: (A, String) => Either[String, Unit]): Either[String, Unit] =
    values.zipWithIndex.traverse_ { case (value, index) => validate(value, s"$path[$index]") }

  private def unique[A](values: Vector[A], path: String): Either[String, Unit] =
    rule(values.distinct.size == values.size, path, "编号或关联重复")

  private def finite(p: Vec3): Boolean = Vector(p.x, p.y, p.z).forall(_.isFinite)

  // Shared by imports and stored revisions; the Boolean entry remains for existing callers.
  private[cases] def valid(record: CaseRevisionRecord): Boolean = validation(record).isRight

  private[cases] def validation(record: CaseRevisionRecord): Either[String, Unit] =
    val f = record.content
    for
      _ <- validateHeader(record)
      _ <- validateScene(f)
      _ <- validateClues(f)
      _ <- validateDialogue(f)
      _ <- validateExecution(f)
      _ <- validateStage(f)
      _ <- validateChecks(f)
      _ <- validateMap(f)
      _ <- validateFollowUps(f)
      _ <- validateStatementComparisons(f)
    yield ()

  private def validateHeader(record: CaseRevisionRecord): Either[String, Unit] =
    val f = record.content
    for
      _ <- rule(record.revision.value > 0, "revision", "修订号必须大于零")
      _ <- rule(record.contentSchemaVersion == 1, "contentSchemaVersion", "不支持的存储版本")
      _ <- rule(record.title.trim.nonEmpty, "title", "标题不能为空")
      _ <- rule(f.placeName.trim.nonEmpty, "placeName", "地点名称不能为空")
      _ <- rule(f.timeOriginLabel.trim.nonEmpty, "timeOriginLabel", "时间原点说明不能为空")
      _ <- rule(finite(f.spawnPosition), "investigation.initial.spawnPosition", "坐标必须是有限数值")
    yield ()

  private def validateScene(f: CaseContent): Either[String, Unit] =
    val nodes = f.nodes.map(n => n.id -> n).toMap
    def validChain(id: SceneNodeId, seen: Set[SceneNodeId]): Boolean =
      !seen(id) && nodes.get(id).exists(n => n.place == f.place && n.parent.forall(validChain(_, seen + id)))
    for
      _ <- unique(f.nodes.map(_.id), "scene.nodes")
      _ <- unique(f.points.map(_.id), "scene.points")
      _ <- entries(f.nodes, "scene.nodes") { (n, path) =>
        for
          _ <- rule(finite(n.position), s"$path.transform.position", "坐标必须是有限数值")
          _ <- rule(validChain(n.id, Set.empty), s"$path.parent", "父链有循环、缺失节点或跨地点引用")
        yield ()
      }
      _ <- entries(f.points, "scene.points") { (p, path) =>
        for
          _ <- rule(nodes.contains(p.node), s"$path.entry.node", "引用了不存在的场景节点")
          _ <- rule(finite(p.localPoint), s"$path.entry.localPoint", "坐标必须是有限数值")
          _ <- rule(p.radiusMeters.isFinite && p.radiusMeters > 0, s"$path.entry.radiusMeters", "交互半径必须是正的有限数值")
        yield ()
      }
      _ <- unique(f.observations.map(_.node), "scene.observations")
      _ <- entries(f.observations, "scene.observations") { (o, path) =>
        for
          _ <- rule(nodes.contains(o.node), s"$path.node", "引用了不存在的场景节点")
          _ <- rule(o.label.trim.nonEmpty && o.description.trim.nonEmpty, path, "外观名称和说明不能为空")
        yield ()
      }
      _ <- unique(f.sceneObjects.map(_.node), "scene.objects.node")
      _ <- unique(f.sceneObjects.map(_.entity), "scene.objects.entity")
      _ <- entries(f.sceneObjects, "scene.objects") { (o, path) =>
        for
          _ <- rule(nodes.contains(o.node), s"$path.node", "引用了不存在的场景节点")
          _ <- rule(o.label.trim.nonEmpty, s"$path.label", "物品名称不能为空")
          _ <- rule(o.point.forall(p => f.points.exists(x => x.id == p && x.node == o.node)), s"$path.point", "入口不存在或属于其他节点")
          _ <- rule(o.readRoute.forall(r => f.routes.exists(x => x.id == r && o.point.contains(x.point))), s"$path.readRoute", "读取路线不存在或属于其他入口")
          _ <- rule(o.point.nonEmpty || f.observations.exists(_.node == o.node), path, "物品需要调查入口或已登记外观")
        yield ()
      }
    yield ()

  private def validateClues(f: CaseContent): Either[String, Unit] =
    val facts = f.facts.map(_.id).toSet
    for
      _ <- unique(f.facts.map(_.id), "truth.facts")
      _ <- unique(f.clues.map(_.id), "investigation.clues")
      _ <- entries(f.clues, "investigation.clues") { (c, path) =>
        rule(c.facts.subsetOf(facts), s"$path.facts", "引用了不存在的事实")
      }
      _ <- rule(f.commonFacts.subsetOf(facts), "publicInfo.background.commonKnowledge", "引用了不存在的事实")
    yield ()

  private def validateDialogue(f: CaseContent): Either[String, Unit] =
    val clues = f.clues.map(_.id).toSet
    for
      _ <- unique(f.publicPersons.map(_.id), "publicInfo.background.persons")
      _ <- entries(f.publicPersons, "publicInfo.background.persons") { (p, path) =>
        for
          _ <- rule(p.id.value.matches("[a-z][a-z0-9_]{0,63}"), s"$path.id", "人物编号格式不合法")
          _ <- rule(Vector(p.name,p.identity,p.victimRelation,p.caseRelation).forall(_.trim.nonEmpty), path, "公开人物说明不能为空")
          _ <- rule(p.witness.forall(id => f.witnesses.exists(_.id == id) || (id == WitnessId("melchett") && f.police.nonEmpty)), s"$path.witness", "引用了不存在的问询对象")
        yield ()
      }
      _ <- f.police.traverse_ { p =>
        for
          _ <- rule(p.name.trim.nonEmpty && p.background.trim.nonEmpty, "investigation.police", "警方姓名和背景不能为空")
          _ <- rule(f.points.exists(_.id == p.point) && !f.witnesses.exists(_.point == p.point), "investigation.police.point", "警方入口不存在或与人物入口重叠")
        yield ()
      }
      _ <- unique(f.witnesses.map(_.id), "investigation.witnesses")
      _ <- unique(f.witnesses.map(_.point), "investigation.witnesses.point")
      _ <- entries(f.witnesses, "investigation.witnesses") { (w, path) =>
        for
          _ <- rule(w.id.value.matches("[a-z][a-z0-9_]{0,63}"), s"$path.id", "问询对象编号格式不合法")
          _ <- rule(Vector(w.name,w.background,w.voice).forall(_.trim.nonEmpty), path, "人物姓名、背景和语气不能为空")
          _ <- rule(f.points.exists(_.id == w.point), s"$path.point", "人物入口不存在")
          _ <- rule(w.answers.nonEmpty, s"$path.answers", "合法答复不能为空")
          _ <- unique(w.answers.map(_.id), s"$path.answers")
          _ <- entries(w.answers, s"$path.answers") { (a, answerPath) =>
            for
              _ <- rule(a.id.value.matches("[a-z][a-z0-9_]{0,99}"), s"$answerPath.id", "答复编号格式不合法")
              _ <- rule(a.text.trim.nonEmpty && a.text.length <= 1500, s"$answerPath.text", "答复正文长度不合法")
              _ <- rule(a.requiredEvidence.subsetOf(clues), s"$answerPath.requiredEvidence", "引用了不存在的线索")
              _ <- unique(a.routes, s"$answerPath.routes")
              _ <- rule(a.routes.forall(id => f.routes.exists(r => r.id == id && r.point == w.point)), s"$answerPath.routes", "路线不存在或不属于该人物")
            yield ()
          }
        yield ()
      }
    yield ()

  private def validateExecution(f: CaseContent): Either[String, Unit] =
    val clues = f.clues.map(_.id).toSet
    for
      _ <- unique(f.routes.map(_.id), "investigation.routes")
      _ <- unique(f.operations.map(_.id), "investigation.operations")
      _ <- entries(f.routes, "investigation.routes") { (r, path) =>
        for
          _ <- rule(f.points.exists(_.id == r.point), s"$path.point", "调查入口不存在")
          _ <- rule(r.prerequisite.forall(op => f.operations.exists(_.id == op)), s"$path.prerequisite", "前置操作不存在")
          _ <- rule(r.disabledAfter.forall(op => f.operations.exists(_.id == op)), s"$path.disabledAfter", "阶段操作不存在")
          _ <- rule(r.clues.forall(clues.contains) && r.requiredClues.subsetOf(clues), path, "授予或所需线索不存在")
          _ <- rule(r.requiredClues.intersect(r.clues.toSet).isEmpty, s"$path.requiredClues", "不能要求先取得本路线授予的线索")
          _ <- rule(r.presentedClues.subsetOf(r.requiredClues), s"$path.presentedClues", "出示材料必须包含在所需材料中")
        yield ()
      }
      _ <- entries(f.operations, "investigation.operations") { (o, path) =>
        for
          _ <- rule(o.effects.nonEmpty || f.followUps.exists(_.operation == o.id), s"$path.effects", "只有已登记补充问询允许无实体效果")
          _ <- rule(f.points.exists(_.id == o.point), s"$path.point", "操作入口不存在")
          _ <- rule(o.requiredClues.subsetOf(clues), s"$path.requiredClues", "所需线索不存在")
          _ <- entries(o.effects, s"$path.effects") { (effect, effectPath) => effect match
            case FixtureEffect.SetNodeVisible(node, _) => rule(f.nodes.exists(_.id == node), s"$effectPath.node", "目标节点不存在")
            case _ => Right(())
          }
        yield ()
      }
    yield ()

  private def validateStage(f: CaseContent): Either[String, Unit] =
    val clues = f.clues.map(_.id).toSet
    f.stage.traverse_ { s =>
      for
        _ <- rule(s.introduction.trim.nonEmpty && s.objective.trim.nonEmpty, "investigation.stage", "阶段简介和目标不能为空")
        _ <- rule(clues(s.completionClue), "investigation.stage.completionClue", "完成材料不存在")
        _ <- s.comparison.traverse_ { c =>
          val path = "investigation.stage.comparison"
          for
            _ <- rule(f.points.exists(_.id == c.point), s"$path.point", "比较入口不存在")
            _ <- rule(!f.routes.exists(_.id == c.route), s"$path.route", "比较路线不能与普通路线重名")
            _ <- rule(c.requiredClues.nonEmpty && c.evidenceClues.nonEmpty, path, "比较前提和证据不能为空")
            _ <- rule(c.evidenceClues.subsetOf(c.requiredClues) && c.requiredClues.subsetOf(clues), path, "比较证据未包含于前提或引用未知线索")
            _ <- rule(!c.requiredClues(s.completionClue), s"$path.requiredClues", "不能把比较结果作为自身前提")
            _ <- rule(c.convertedTimeMinutes >= 0 && c.convertedTimeMinutes < 1440, s"$path.convertedTimeMinutes", "分钟数必须在一天内")
            _ <- rule(!f.routes.exists(_.clues.contains(s.completionClue)), "investigation.stage.completionClue", "比较结果不能从普通路线提前取得")
          yield ()
        }
      yield ()
    }

  private def validateChecks(f: CaseContent): Either[String, Unit] =
    val clues = f.clues.map(_.id).toSet
    for
      _ <- f.clock.traverse_ { c =>
        for
          _ <- rule(c.startMinutesOfDay >= 0 && c.startMinutesOfDay < 1440, "investigation.clock.startMinutesOfDay", "起始分钟必须在一天内")
          _ <- rule(c.actionSeconds >= 60 && c.actionSeconds <= 600 && c.actionSeconds % 60 == 0, "investigation.clock.actionSeconds", "行动耗时必须为1至10分钟的整分钟")
        yield ()
      }
      _ <- rule(f.externalChecks.isEmpty || (f.clock.nonEmpty && f.police.nonEmpty), "investigation.externalChecks", "外部核查需要案件时钟和警方入口")
      _ <- unique(f.externalChecks.map(_.id), "investigation.externalChecks")
      _ <- entries(f.externalChecks, "investigation.externalChecks") { (c, path) =>
        for
          _ <- rule(c.title.trim.nonEmpty && c.requestLabel.trim.nonEmpty, path, "核查名称和申请说明不能为空")
          _ <- rule(c.delaySeconds >= 60 && c.delaySeconds <= 7200 && c.delaySeconds % 60 == 0, s"$path.delaySeconds", "预计耗时必须为1至120分钟的整分钟")
          _ <- rule(f.police.exists(_.point == c.point), s"$path.point", "核查入口必须属于警方")
          _ <- rule(c.requiredClues.subsetOf(clues), s"$path.requiredClues", "所需线索不存在")
          _ <- rule(c.resultClues.nonEmpty && c.resultClues.subsetOf(clues), s"$path.resultClues", "核查结果为空或引用未知线索")
          _ <- rule(c.requiredClues.intersect(c.resultClues).isEmpty, s"$path.requiredClues", "核查不能依赖自身结果")
          _ <- rule(!f.routes.exists(r => r.clues.exists(c.resultClues)), s"$path.resultClues", "核查结果不能从普通路线提前取得")
        yield ()
      }
    yield ()

  private def validateMap(f: CaseContent): Either[String, Unit] =
    f.areaMap.traverse_ { m =>
      val path = "publicInfo.background.areaMap"
      for
        _ <- rule(m.note.trim.nonEmpty && m.locations.nonEmpty, path, "地图说明和地点不能为空")
        _ <- unique(m.locations.map(_.id), s"$path.locations")
        _ <- unique(m.roads.map(_.id), s"$path.roads")
        _ <- entries(m.locations, s"$path.locations") { (l, locationPath) =>
          for
            _ <- rule(l.name.trim.nonEmpty && l.description.trim.nonEmpty, locationPath, "地点名称和说明不能为空")
            _ <- rule(l.x.isFinite && l.y.isFinite && l.x >= 40 && l.x <= 1160 && l.y >= 40 && l.y <= 660, locationPath, "地点坐标超出地图范围")
          yield ()
        }
        _ <- entries(m.roads, s"$path.roads") { (r, roadPath) =>
          for
            _ <- rule(r.id.matches("[a-z][a-z0-9_]{0,63}"), s"$roadPath.id", "道路编号格式不合法")
            _ <- rule(r.from != r.to && m.locations.exists(_.id == r.from) && m.locations.exists(_.id == r.to), roadPath, "道路端点不存在或为同一地点")
            _ <- rule(r.meters > 0 && r.meters <= 10000, s"$roadPath.meters", "道路距离必须大于零且不超过10000米")
            _ <- rule(r.label.trim.nonEmpty, s"$roadPath.label", "道路说明不能为空")
          yield ()
        }
      yield ()
    }

  private def validateFollowUps(f: CaseContent): Either[String, Unit] =
    for
      _ <- unique(f.followUps.map(_.operation), "investigation.followUps.operation")
      _ <- unique(f.followUps.map(_.witness), "investigation.followUps.witness")
      _ <- entries(f.followUps, "investigation.followUps") { (p, path) =>
        val witness = f.witnesses.find(_.id == p.witness)
        val operation = f.operations.find(_.id == p.operation)
        for
          _ <- rule(p.title.trim.nonEmpty, s"$path.title", "补充问询标题不能为空")
          _ <- rule(p.requiredEvidence.nonEmpty && p.requiredRoutes.nonEmpty, path, "所需材料和关键回应不能为空")
          _ <- rule(witness.nonEmpty, s"$path.witness", "问询对象不存在")
          _ <- rule(operation.exists(o => witness.exists(_.point == o.point) && !o.repeatable), s"$path.operation", "开始操作不存在、不属于该人物或允许重复执行")
          _ <- rule(operation.exists(o => p.requiredEvidence.subsetOf(o.requiredClues)), s"$path.requiredEvidence", "材料未包含于开始操作前提")
          _ <- rule(p.requiredRoutes.forall(id => f.routes.exists(r => r.id == id && !r.prerequisite.contains(p.operation) && witness.exists(_.point == r.point) &&
            operation.exists(o => r.clues.toSet.subsetOf(o.requiredClues)))), s"$path.requiredRoutes", "关键路线不存在、不属于该人物、依赖本阶段或结果未包含于开始前提")
          _ <- rule(Vector(p.knowledge,p.policy).forall(xs => xs.nonEmpty && xs.size <= 30 && xs.forall(s => s.trim.nonEmpty && s.length <= 1200)), path, "阶段知识或策略的条数／长度不合法")
        yield ()
      }
    yield ()

  private def validateStatementComparisons(f: CaseContent): Either[String, Unit] =
    for
      _ <- unique(f.statementComparisons.map(_.witness), "investigation.statementComparisons.witness")
      _ <- entries(f.statementComparisons, "investigation.statementComparisons") { (p, path) =>
        for
          _ <- rule(f.witnesses.exists(_.id == p.witness), s"$path.witness", "问询对象不存在")
          _ <- rule(p.title.trim.nonEmpty && p.nextObjective.trim.nonEmpty && p.rows.nonEmpty, path, "对照标题、后续方向和行不能为空")
          _ <- unique(p.rows.map(_.label), s"$path.rows.label")
          _ <- entries(p.rows, s"$path.rows") { (r, rowPath) =>
            for
              _ <- rule(r.label.trim.nonEmpty && r.initial != r.revised, rowPath, "行标题不能为空且两版本不能相同")
              _ <- rule(Vector(r.initial,r.revised).forall(id => f.clues.exists(_.id == id) && f.routes.exists(route => route.clues.contains(id) && f.witnesses.exists(w => w.id == p.witness && w.point == route.point))), rowPath, "对照材料不存在或不属于该人物")
              _ <- rule(f.followUps.find(_.witness == p.witness).exists(phase => f.routes.filter(_.clues.contains(r.revised)).forall(_.prerequisite.contains(phase.operation))), s"$rowPath.revised", "补充版本缺少本人补充问询阶段前提")
            yield ()
          }
        yield ()
      }
    yield ()
