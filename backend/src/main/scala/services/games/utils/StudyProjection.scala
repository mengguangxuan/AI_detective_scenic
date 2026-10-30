package services.games.utils
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.system.objects.*
// Authored presentation binding for the first registered study, not a general action classifier.
private[games] object StudyProjection:
  private[games] val caseId = CaseId("vicarage_study")
  private[games] val revision = Revision(11)
  // Supported content revisions share study/4. Existing games retain their authored content.
  private[games] def supports(id: CaseId, value: Revision): Boolean =
    id == caseId && Set(Revision(3), Revision(4), Revision(5), Revision(6), Revision(7), Revision(8), Revision(9), Revision(10), revision)(value)
  private[games] def snapshot(caseRecord: CaseRevisionRecord, state: GameSessionRecord, records: Vector[SessionRecord], turns: Vector[InterviewTurnRecord], exchanges: Vector[PoliceExchangeRecord] = Vector.empty): InvestigationSnapshot =
    if !supports(caseRecord.caseId,caseRecord.revision) then
      throw GameError(ErrorCode.ConflictingContent,"这份调查使用的场景版本暂不支持，请保留原存档；可以另建一次调查使用当前版本。")
    val content = caseRecord.content
    val police = content.police.flatMap(id => content.witnesses.find(_.id == id))
    val positions = SceneAccessRules.positions(content,state)
    val points = content.points.filter(p => SceneAccessRules.withinRange(positions.get(p.node),state.playerPosition,p)).map { point =>
      val operations = content.operations.filter(o => o.point == point.id && FixtureRules.operationAvailable(state,o) && FollowUpRules.permitted(content,state,o.id,records,turns))
        .map(o => OperationChoiceView(o.id,o.label))
      val routes = content.routes.filter(r => r.point == point.id && FixtureRules.routeAvailable(state,r) && !r.clues.forall(state.clues.contains))
        .map(r => RouteChoiceView(r.id,r.label))
      val pointClues = content.routes.filter(_.point == point.id).flatMap(_.clues).toSet ++ content.externalChecks.filter(_.point == point.id).flatMap(_.resultClues)
      val findings = content.clues.filter(c => pointClues(c.id) && state.clues(c.id)).map(c => ClueView(c.id,c.title.value,c.text.value))
      InquiryPointView(point.id,point.label,operations,routes,findings)
    }
    val clues = content.clues.filter(c => state.clues(c.id)).map(c => ClueView(c.id,c.title.value,c.text.value))
    val observations = content.observations.filter(o => positions.contains(o.node))
    val journal = records.filter(_.sequence.value <= state.sequence.value).map(r => InvestigationLogEntry(
      r.id,r.sequence,if content.clock.nonEmpty then ExternalCheckRules.timeLabel(content,r.storyTimeSeconds) else s"${content.timeOriginLabel} +${r.storyTimeSeconds - content.initialStoryTimeSeconds}秒",r.text,r.disclosedClues))
    val accessibleWitnesses = content.witnesses.filter(w => points.exists(_.id == w.point)).map(_.id).toSet
    val people = content.publicPersons.map(p => p.copy(witness=p.witness.filter(accessibleWitnesses)))
    InvestigationSnapshot(state.id,state.caseId,state.caseRevision,state.sequence,caseRecord.title,
      StudySceneView("study","4",content.placeName,state.opened(EntityId("drawer")),state.opened(EntityId("window")),
        content.sceneObjects.filter(o => positions.contains(o.node) && o.point.forall(p => points.exists(_.id == p)))),points,clues,journal,observations,
      content.stage.map(s => InvestigationStageView(s.introduction,s.objective,state.clues(s.completionClue),s.nextObjective)),
      content.witnesses.filter(w => points.exists(_.id == w.point)).map(w => WitnessView(w.id,w.point,w.name,w.background)),
      turns.filter(_.sequence.value <= state.sequence.value).map(t => InterviewTurnView(t.requestId,t.sequence,t.witness,t.question,t.shownClues,t.reply,t.mode)),
      police.filter(p => points.exists(_.id == p.point)).map { p =>
        val visible = exchanges.filter(_.sequence.value <= state.sequence.value)
        PoliceDeskView(p.point,p.name,p.background,visible.flatMap(_.evidence).toSet,
          visible.map(e => PoliceExchangeView(e.requestId,e.sequence,e.evidence,e.reply)))
      },people,content.areaMap,
      content.clock.map(c => ClockView(ExternalCheckRules.timeLabel(content,state.storyTimeSeconds),state.storyTimeSeconds-content.initialStoryTimeSeconds,c.actionSeconds/60)),
      ExternalCheckRules.views(content,state),FollowUpRules.views(content,state,records,turns),FollowUpRules.comparisons(content,state,records))
