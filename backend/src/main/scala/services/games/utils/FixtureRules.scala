package services.games.utils

import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.system.objects.*

private[games] object FixtureRules:
  private def denied = GameError(ErrorCode.AccessDenied, "当前无法访问该调查入口。")

  private[games] def initial(f: InvestigationFixture): FixtureState =
    FixtureState(Sequence(0), f.place, f.spawnPosition, f.opened, f.unlocked,
      f.nodes.filter(_.initiallyVisible).map(_.id).toSet, Set.empty, Set.empty, f.commonFacts)

  private[games] def accessible(f: InvestigationFixture, state: FixtureState, point: FixturePoint): Boolean =
    val nodes = f.nodes.map(n => n.id -> n).toMap
    def position(id: SceneNodeId): Option[Vec3] =
      nodes.get(id).filter(n => n.place == state.playerPlace && state.visibleNodes(n.id)).flatMap { n =>
        n.parent match
          case None => Some(n.position)
          case Some(parent) => position(parent).map(p => Vec3(p.x + n.position.x, p.y + n.position.y, p.z + n.position.z))
      }
    position(point.node).exists { p =>
      val dx = p.x + point.localPoint.x - state.playerPosition.x
      val dy = p.y + point.localPoint.y - state.playerPosition.y
      val dz = p.z + point.localPoint.z - state.playerPosition.z
      dx * dx + dy * dy + dz * dz <= point.radiusMeters * point.radiusMeters
    }

  private[games] def open(f: InvestigationFixture, state: FixtureState, operation: OperationId): Either[GameError, Vector[FixtureChange]] =
    for
      op <- f.operations.find(_.id == operation).toRight(denied)
      point <- f.points.find(_.id == op.point).filter(accessible(f, state, _)).toRight(denied)
      _ <- Either.cond(!state.completedOperations(operation), (), GameError(ErrorCode.ConditionFailed, "该操作已完成。"))
    yield Vector(FixtureChange.WorldChanged(op.effects), FixtureChange.OperationCompleted(operation))

  private[games] def investigate(f: InvestigationFixture, state: FixtureState, request: RequestId,
      pointId: PointId, routeId: RouteId): Either[GameError, Vector[FixtureChange]] =
    for
      point <- f.points.find(_.id == pointId).filter(accessible(f, state, _)).toRight(denied)
      route <- f.routes.find(r => r.id == routeId && r.point == pointId).toRight(denied)
      _ <- Either.cond(state.completedOperations(route.prerequisite), (), GameError(ErrorCode.ConditionFailed, "需要先完成前置操作。"))
    yield
      val clues = f.clues.filter(c => route.clues.contains(c.id))
      val record = FixtureInvestigationRecord(RecordId(s"${request.value}:investigation"), pointId, routeId,
        Text(clues.map(_.text.value).mkString("\n")), clues.map(_.id).toSet)
      val acquired = FixtureAcquisition(RecordId(s"${request.value}:acquisition"), record.id,
        clues.map(_.id).toSet, clues.flatMap(_.facts).toSet)
      Vector(FixtureChange.Investigated(record), FixtureChange.Acquired(acquired))

  private[games] def replay(session: StoredSession, commits: Vector[FixtureCommit]): Either[GameError, FixtureState] =
    commits.foldLeft[Either[GameError, FixtureState]](Right(session.initialState)) { (acc, commit) =>
      acc.flatMap { state =>
        if commit.session != session.id || commit.sequence.value != state.sequence.value + 1 || commit.reducerVersion != session.fixture.reducerVersion then
          Left(GameError(ErrorCode.ReplayVersionMismatch, "日志版本或顺序不一致。"))
        else Right(applyChanges(state, commit.changes).copy(sequence = commit.sequence))
      }
    }.flatMap(state => Either.cond(state.sequence == session.sequence, state,
      GameError(ErrorCode.ReplayVersionMismatch, "会话序号与日志不一致。")))

  private def applyChanges(initial: FixtureState, changes: Vector[FixtureChange]): FixtureState =
    changes.foldLeft(initial) { (state, change) =>
      change match
        case FixtureChange.WorldChanged(effects) => effects.foldLeft(state) { (s, effect) => effect match
          case FixtureEffect.Open(entity) => s.copy(opened = s.opened + entity)
          case FixtureEffect.SetNodeVisible(node, true) => s.copy(visibleNodes = s.visibleNodes + node)
          case FixtureEffect.SetNodeVisible(node, false) => s.copy(visibleNodes = s.visibleNodes - node)
        }
        case FixtureChange.OperationCompleted(operation) => state.copy(completedOperations = state.completedOperations + operation)
        case FixtureChange.Investigated(_) => state // Attempts alone never grant knowledge.
        case FixtureChange.Acquired(record) => state.copy(clues = state.clues ++ record.clues, facts = state.facts ++ record.facts)
    }

  private[games] def result(sequence: Sequence, changes: Vector[FixtureChange]): CommandResult =
    CommandResult(sequence,
      changes.collect { case FixtureChange.Investigated(record) => record.text },
      changes.collect { case FixtureChange.Acquired(record) => record.clues }.flatten.toSet)

  private[games] def view(f: InvestigationFixture, state: FixtureState): GetGameStateResponse =
    GetGameStateResponse(state.sequence, f.points.filter(accessible(f, state, _)).map(_.id), state.clues, state.facts)
