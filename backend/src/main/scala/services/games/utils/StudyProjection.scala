package services.games.utils
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.system.objects.*
// Authored presentation binding for the first registered study, not a general action classifier.
private[games] object StudyProjection:
  private[games] val caseId = CaseId("vicarage_study")
  // Legacy openstudy payloads have no explicit revision; retain their original default.
  private[games] val revision = Revision(21)
  // Revision 18 adds study/5; existing games retain study/4 and their authored content.
  private[games] def supports(id: CaseId, value: Revision): Boolean =
    id == caseId && Set(Revision(3), Revision(4), Revision(5), Revision(6), Revision(7), Revision(8), Revision(9), Revision(10), Revision(11), Revision(12), Revision(13), Revision(14), Revision(15), Revision(16), Revision(17), Revision(18), Revision(19), Revision(20), Revision(22), Revision(23), Revision(24), Revision(25), Revision(26), revision)(value)
  private[games] def supportsScene(bundle: String, revision: String): Boolean = bundle == "study" && Set("4","5","6")(revision)
  private[games] def snapshot(caseRecord: CaseRevisionRecord, state: GameSessionRecord, records: Vector[SessionRecord], turns: Vector[InterviewTurnRecord], exchanges: Vector[PoliceExchangeRecord] = Vector.empty,
      release: Option[CaseReleaseRecord] = None): InvestigationSnapshot =
    val supported = release.fold(supports(caseRecord.caseId,caseRecord.revision))(r =>
      r.caseId == caseRecord.caseId && r.revision == caseRecord.revision && supportsScene(r.sceneBundle,r.sceneRevision))
    if !supported then
      throw GameError(ErrorCode.ConflictingContent,"这份调查使用的场景版本暂不支持，请保留原存档；可以另建一次调查使用当前版本。")
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
      val presentations = records.filter(_.point == point.id).flatMap(r => r.route.flatMap(id =>
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
      r.id,r.sequence,if content.clock.nonEmpty then ExternalCheckRules.timeLabel(content,r.storyTimeSeconds) else s"${content.timeOriginLabel} +${r.storyTimeSeconds - content.initialStoryTimeSeconds}秒",r.text,r.disclosedClues))
    val accessibleWitnesses = content.witnesses.filter(w => points.exists(_.id == w.point)).map(_.id).toSet
    val people = content.publicPersons.map(p => p.copy(witness=p.witness.filter(accessibleWitnesses)))
    InvestigationSnapshot(state.id,state.caseId,state.caseRevision,state.sequence,caseRecord.title,
      StudySceneView(location.map(_.bundleId).getOrElse(release.map(_.sceneBundle).getOrElse("study")),location.map(_.revision).getOrElse(release.map(_.sceneRevision).getOrElse(if caseRecord.revision.value >= 18 then "5" else "4")),location.map(_.name).getOrElse(content.placeName),state.opened(EntityId("drawer")),state.opened(EntityId("window")),
        content.sceneObjects.filter(o => positions.contains(o.node) && o.point.forall(p => points.exists(_.id == p))),Option.when(location.nonEmpty)(state.playerPosition),travel),points,clues,journal,observations,
      content.stage.map(s => InvestigationStageView(s.introduction,s.objective,s.completionClue.exists(state.clues.contains),s.nextObjective,
        Option.when(s.comparison.isEmpty)(false))),
      knownWitnesses.map(w => WitnessView(w.id,w.point,w.name,w.background)),
      turns.filter(_.sequence.value <= state.sequence.value).map(t => InterviewTurnView(t.requestId,t.sequence,t.witness,t.question,t.shownClues,t.reply,t.mode,t.recognition)),
      police.filter(p => points.exists(_.id == p.point)).map { p =>
        val visible = exchanges.filter(_.sequence.value <= state.sequence.value)
        PoliceDeskView(p.point,p.name,p.background,visible.flatMap(_.evidence).toSet,
          visible.map(e => PoliceExchangeView(e.requestId,e.sequence,e.evidence,e.reply)))
      },people,content.areaMap,
      content.clock.map(c => ClockView(ExternalCheckRules.timeLabel(content,state.storyTimeSeconds),state.storyTimeSeconds-content.initialStoryTimeSeconds,c.actionSeconds/60)),
      ExternalCheckRules.views(content,state),FollowUpRules.views(content,state,records,turns),FollowUpRules.comparisons(content,state,records),content.conclusion.map(_.candidates).getOrElse(Set.empty),state.conclusion,content.conclusion.flatMap(_.evaluation).map(_ => state.reasoning),
      content.continuousInterviews.map(_.witness).filter(accessibleWitnesses))
