package services.games.utils
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.system.objects.*
// Shared authorized view. Scene-specific instance state remains a separate decoupling task.
private[games] object InvestigationProjection:
  private[games] def supportsScene(bundle: String): Boolean = Set("study","woodland","authored")(bundle)
  private[games] def snapshot(caseRecord: CaseRecord, state: GameSessionRecord, records: Vector[SessionRecord], turns: Vector[InterviewTurnRecord], exchanges: Vector[PoliceExchangeRecord] = Vector.empty,
      release: Option[CaseReleaseRecord] = None): InvestigationSnapshot =
    val supported = supportsScene(caseRecord.content.sceneBundle) && release.forall(r =>
      r.caseId == caseRecord.caseId && r.sceneBundle == caseRecord.content.sceneBundle)
    if !supported then throw GameError(ErrorCode.ConflictingContent,"案件的场景能力或发布绑定不匹配。")
    val content = RecordedInterviewRules.content(caseRecord.content,turns)
    val checkState=SemanticEvidence.forChecks(content,state)
    val location=content.sceneLocations.find(_.place == state.playerPlace)
    val police = content.police.flatMap(id => content.witnesses.find(_.id == id))
    val positions = SceneAccessRules.positions(content,state)
    val points = content.points.filter(p => SceneAccessRules.withinRange(positions.get(p.node),state.playerPosition,p)).map { point =>
      val operations = content.operations.filter(o => o.point == point.id && FixtureRules.operationAvailable(checkState,o) && FollowUpRules.permitted(content,state,o.id,records,turns))
        .map(o => OperationChoiceView(o.id,o.label))
      val routes = content.routes.filter(r => r.point == point.id && FixtureRules.routeAvailable(checkState,r) && FixtureRules.routePending(content,state,r,records,turns))
        .map(r => RouteChoiceView(r.id,r.label))
      val pointClues = content.routes.filter(_.point == point.id).flatMap(_.clues).toSet ++ content.externalChecks.filter(_.point == point.id).flatMap(_.resultClues)
      val findings = content.clues.filter(c => pointClues(c.id) && state.clues(c.id)).map(c => ClueView(c.id,c.title.value,c.text.value))
      val presentations = records.filter(_.point.contains(point.id)).flatMap(r => r.route.flatMap(id =>
        content.routes.find(route => route.id == id && route.clues.isEmpty).map(route => RecordedPresentationView(r.sequence,if content.interviewPromptVersion >= 3 then turns.find(_.requestId == r.requestId).map(_.shownClues).getOrElse(SemanticEvidence.resolve(content,route.presentedClues,state.clues).getOrElse(Set.empty)) else route.presentedClues,r.text))))
      InquiryPointView(point.id,point.label,operations,routes,findings,presentations)
    }
    val travel=points.flatMap(p => content.operations.filter(o => o.point == p.id && p.operations.exists(_.id == o.id)).flatMap(o =>
      o.effects.collect { case services.cases.objects.investigation.FixtureEffect.Relocate(place,_) =>
        TravelChoiceView(o.id,p.id,o.label,place,content.sceneLocations.find(_.place == place).map(_.name).getOrElse(place.value)) }))
    val knownWitnesses=if content.sceneLocations.isEmpty then content.witnesses.filter(w => points.exists(_.id == w.point)) else
      content.witnesses.filter(w => content.points.find(_.id == w.point).exists(p => content.nodes.find(_.id == p.node).exists(n =>
        SceneAccessRules.positions(content,state.copy(playerPlace=n.place)).contains(p.node))))
    val clues = content.clues.filter(c => state.clues(c.id)).map(c => ClueView(c.id,c.title.value,c.text.value))
    val observations = content.observations.filter(o => positions.contains(o.node))
    val journal = records.filter(_.sequence.value <= state.sequence.value).map(r => InvestigationLogEntry(
      r.id,r.sequence,ExternalCheckRules.timeLabel(content,r.storyTimeSeconds),r.text,r.disclosedClues))
    val accessibleWitnesses = content.witnesses.filter(w => points.exists(_.id == w.point)).map(_.id).toSet
    val people = content.publicPersons.map(p => p.copy(witness=p.witness.filter(accessibleWitnesses)))
    val visibleObjects=content.sceneObjects.filter(o => positions.contains(o.node) && o.point.forall(p => points.exists(_.id == p)))
    val render=content.sceneBlueprint.flatMap(b => b.rooms.find(_.place == state.playerPlace).map(room =>
      SceneRenderView(room,b.visuals.filter(v => visibleObjects.exists(_.node == v.node)).map(v => SceneInstanceView(v.node,positions(v.node),v.parts)))))
    InvestigationSnapshot(state.id,state.caseId,state.sequence,caseRecord.title,
      SceneView(location.map(_.bundleId).getOrElse(content.sceneBundle),location.map(_.name).getOrElse(content.placeName),state.opened.intersect(visibleObjects.map(_.entity).toSet),
        visibleObjects,Option.when(location.nonEmpty)(state.playerPosition),travel,Some(state.playerPlace),render),points,clues,journal,observations,
      content.stage.map(s => InvestigationStageView(s.introduction,s.objective,s.completionClue.exists(state.clues.contains),s.nextObjective,
        Option.when(s.comparison.isEmpty)(false))),
      knownWitnesses.map(w => WitnessView(w.id,w.point,w.name,w.background)),
      turns.filter(_.sequence.value <= state.sequence.value).map(t => InterviewTurnView(t.requestId,t.sequence,t.witness,t.question,t.shownClues,t.reply,t.mode,t.recognition)),
      police.filter(p => points.exists(_.id == p.point)).map { p =>
        val visible = exchanges.filter(_.sequence.value <= state.sequence.value)
        PoliceDeskView(p.point,p.name,p.background,visible.flatMap(_.evidence).toSet,
          visible.map(e => PoliceExchangeView(e.requestId,e.sequence,e.evidence,e.reply)))
      },people,content.areaMap,
      content.clock.map(c => ClockView(ExternalCheckRules.timeLabel(content,state.storyTimeSeconds),state.storyTimeSeconds-content.initialStoryTimeSeconds,c.actionSeconds/60,
        c.playableUntilSeconds.map(end => math.max(0L,math.min(30L,(end-state.storyTimeSeconds)/60)).toInt).getOrElse(30),c.playableUntilSeconds.map(ExternalCheckRules.timeLabel(content,_)),c.targets.map(t => ClockTargetView(t.label,ExternalCheckRules.timeLabel(content,t.seconds))))),
      ExternalCheckRules.views(content,state),FollowUpRules.views(content,state,records,turns),FollowUpRules.comparisons(content,state,records),content.conclusion.map(_.candidates).getOrElse(Set.empty),state.conclusion,content.conclusion.flatMap(_.evaluation).map(_ => state.reasoning),
      content.continuousInterviews.map(_.witness).filter(accessibleWitnesses))
