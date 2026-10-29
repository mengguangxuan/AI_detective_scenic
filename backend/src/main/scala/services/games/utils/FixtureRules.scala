package services.games.utils

import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.system.objects.*

private[games] object FixtureRules:
  private def denied = GameError(ErrorCode.AccessDenied,"当前无法访问该调查入口。")

  private[games] def initial(f: CaseRevisionRecord,id: SessionId,owner: UserId): GameSessionRecord =
    val c=f.content
    GameSessionRecord(id,owner,f.caseId,f.revision,1,Sequence(0),c.initialStoryTimeSeconds,c.place,c.spawnPosition,
      c.initiallyOpened,c.initiallyUnlocked,c.nodes.filter(_.initiallyVisible).map(_.id).toSet,Set.empty,Set.empty,c.commonFacts)

  private[games] def accessible(f: CaseContent,state: GameSessionRecord,point: FixturePoint): Boolean =
    val nodes=f.nodes.map(n => n.id -> n).toMap
    def position(id: SceneNodeId,seen: Set[SceneNodeId]): Option[Vec3] =
      nodes.get(id).filter(n => !seen(id) && n.place==state.playerPlace && state.visibleNodes(n.id)).flatMap { n =>
        n.parent match
          case None => Some(n.position)
          case Some(parent) => position(parent,seen+id).map(p => Vec3(p.x+n.position.x,p.y+n.position.y,p.z+n.position.z))
      }
    position(point.node,Set.empty).exists { p =>
      val dx=p.x+point.localPoint.x-state.playerPosition.x
      val dy=p.y+point.localPoint.y-state.playerPosition.y
      val dz=p.z+point.localPoint.z-state.playerPosition.z
      dx*dx+dy*dy+dz*dz <= point.radiusMeters*point.radiusMeters
    }

  private[games] def operationAvailable(state: GameSessionRecord,operation: FixtureOperation): Boolean =
    (operation.repeatable || !state.completedOperations(operation.id)) &&
      operation.openedRequirement.forall(requirement => state.opened(requirement.entity) == requirement.opened) &&
      operation.requiredClues.subsetOf(state.clues)

  private[games] def routeAvailable(state: GameSessionRecord, route: FixtureRoute): Boolean =
    route.prerequisite.forall(state.completedOperations.contains) && route.disabledAfter.forall(op => !state.completedOperations(op)) && route.requiredClues.subsetOf(state.clues)

  private[games] def execute(f: CaseContent,state: GameSessionRecord,request: RequestId,operation: OperationId, records: Vector[SessionRecord] = Vector.empty, turns: Vector[InterviewTurnRecord] = Vector.empty): Either[GameError,ActionPlan] =
    for
      op <- f.operations.find(_.id==operation).toRight(denied)
      _ <- f.points.find(_.id==op.point).filter(accessible(f,state,_)).toRight(denied)
      _ <- Either.cond(operationAvailable(state,op) && FollowUpRules.permitted(f,state,operation,records,turns),(),GameError(ErrorCode.ConditionFailed,"当前状态不允许执行该操作。"))
    yield
      val changed=op.effects.foldLeft(state) { (s,effect) => effect match
        case FixtureEffect.Open(entity) => s.copy(opened=s.opened+entity)
        case FixtureEffect.SetOpened(entity, opened) => s.copy(opened=if opened then s.opened+entity else s.opened-entity)
        case FixtureEffect.SetNodeVisible(node,true) => s.copy(visibleNodes=s.visibleNodes+node)
        case FixtureEffect.SetNodeVisible(node,false) => s.copy(visibleNodes=s.visibleNodes-node)
      }
      val next=changed.copy(sequence=Sequence(state.sequence.value+1),completedOperations=changed.completedOperations+operation)
      val record=SessionRecord(RecordId(s"${request.value}:operation"),state.id,request,next.sequence,next.storyTimeSeconds,
        op.point,None,Some(operation),op.label,Set.empty,Set.empty)
      ActionPlan(next,record,CommandResult(next.sequence,Vector.empty,Set.empty))

  private[games] def investigate(f: CaseContent,state: GameSessionRecord,request: RequestId,
      pointId: PointId,routeId: RouteId): Either[GameError,ActionPlan] =
    for
      _ <- f.points.find(_.id==pointId).filter(accessible(f,state,_)).toRight(denied)
      route <- f.routes.find(r => r.id==routeId && r.point==pointId).toRight(denied)
      _ <- Either.cond(routeAvailable(state,route),(),GameError(ErrorCode.ConditionFailed,"需要先取得所需材料或完成前置操作。"))
      _ <- Either.cond(!route.clues.forall(state.clues.contains),(),GameError(ErrorCode.ConditionFailed,"该途径的信息已经记录。"))
    yield
      val disclosed=f.clues.filter(c => route.clues.contains(c.id) && !state.clues(c.id))
      val clues=disclosed.map(_.id).toSet
      val facts=disclosed.flatMap(_.facts).toSet
      val text=disclosed.map(_.text.value).mkString("\n")
      val next=state.copy(sequence=Sequence(state.sequence.value+1),clues=state.clues++clues,facts=state.facts++facts)
      val record=SessionRecord(RecordId(s"${request.value}:investigation"),state.id,request,next.sequence,next.storyTimeSeconds,
        pointId,Some(routeId),None,text,clues,facts)
      ActionPlan(next,record,CommandResult(next.sequence,Vector(Text(text)),clues))

  private[games] def view(f: CaseContent,state: GameSessionRecord): GetGameStateResponse =
    GetGameStateResponse(state.sequence,f.points.filter(accessible(f,state,_)).map(_.id),state.clues,state.facts)

  private[games] def compareTimes(f: CaseContent,state: GameSessionRecord,request: RequestId,evidence: Set[ClueId],
      minutes: Int,assessment: TimeAssessment): Either[GameError,ActionPlan] =
    val incomplete = GameError(ErrorCode.ConditionFailed,"这份核对还未完成，请重新检查所选材料、换算时间和判断。")
    for
      stage <- f.stage.toRight(denied)
      spec <- stage.comparison.toRight(denied)
      _ <- f.points.find(_.id == spec.point).filter(accessible(f,state,_)).toRight(denied)
      _ <- Either.cond(!state.clues(stage.completionClue),(),GameError(ErrorCode.ConditionFailed,"这份核对已经记录。"))
      _ <- Either.cond(evidence.subsetOf(state.clues),(),denied)
      _ <- Either.cond(spec.requiredClues.subsetOf(state.clues) && spec.evidenceClues.subsetOf(evidence) &&
        minutes == spec.convertedTimeMinutes && assessment == spec.assessment,(),incomplete)
      clue <- f.clues.find(_.id == stage.completionClue).toRight(denied)
    yield
      val next = state.copy(sequence=Sequence(state.sequence.value+1),clues=state.clues+clue.id,facts=state.facts++clue.facts)
      val cited = evidence.toVector.sortBy(_.value).flatMap(id => f.clues.find(_.id == id).map(_.title.value)).mkString("、")
      val record = SessionRecord(RecordId(s"${request.value}:comparison"),state.id,request,next.sequence,next.storyTimeSeconds,
        spec.point,Some(spec.route),None,s"${clue.text.value}\n引用材料：$cited",Set(clue.id),clue.facts)
      ActionPlan(next,record,CommandResult(next.sequence,Vector(clue.text),Set(clue.id)))
