package services.games.utils
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.system.objects.*
// Authored presentation binding for the first registered study, not a general action classifier.
private[games] object StudyProjection:
  private[games] val caseId = CaseId("fixture_drawer_web")
  private[games] val revision = Revision(2)
  private[games] def snapshot(caseRecord: CaseRevisionRecord, state: GameSessionRecord, records: Vector[SessionRecord]): InvestigationSnapshot =
    require(caseRecord.caseId == caseId && caseRecord.revision == revision,"No registered scene bundle for this case")
    val content = caseRecord.content
    val points = content.points.filter(FixtureRules.accessible(content,state,_)).map { point =>
      val operations = content.operations.filter(o => o.point == point.id && FixtureRules.operationAvailable(state,o))
        .map(o => OperationChoiceView(o.id,o.label))
      val routes = content.routes.filter(r => r.point == point.id && state.completedOperations(r.prerequisite) && !r.clues.forall(state.clues.contains))
        .map(r => RouteChoiceView(r.id,r.label))
      InquiryPointView(point.id,point.label,operations,routes)
    }
    val clues = content.clues.filter(c => state.clues(c.id)).map(c => ClueView(c.id,c.title.value,c.text.value))
    val nodes = content.nodes.map(n => n.id -> n).toMap
    def visible(id: SceneNodeId): Boolean = nodes.get(id).exists(n => state.visibleNodes(id) && n.place == state.playerPlace && n.parent.forall(visible))
    val observations = content.observations.filter(o => visible(o.node))
    val journal = records.filter(_.sequence.value <= state.sequence.value).map(r => InvestigationLogEntry(
      r.id,r.sequence,s"${content.timeOriginLabel} +${r.storyTimeSeconds - content.initialStoryTimeSeconds}秒",r.text,r.disclosedClues))
    InvestigationSnapshot(state.id,state.caseId,state.caseRevision,state.sequence,caseRecord.title,
      StudySceneView("study",caseRecord.revision.value.toString,content.placeName,state.opened(EntityId("drawer")),points.exists(_.id == PointId("point_letter"))),points,clues,journal,observations)
