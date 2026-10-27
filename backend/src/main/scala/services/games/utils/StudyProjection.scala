package services.games.utils
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.system.objects.*
// Authored presentation binding for the first registered study, not a general action classifier.
private[games] object StudyProjection:
  private[games] val caseId = CaseId("fixture_drawer_web")
  private[games] val revision = Revision(1)
  private[games] def snapshot(caseRecord: CaseRevisionRecord, state: GameSessionRecord, records: Vector[SessionRecord]): InvestigationSnapshot =
    require(caseRecord.caseId == caseId && caseRecord.revision == revision,"No registered scene bundle for this case")
    val content = caseRecord.content
    val points = content.points.filter(FixtureRules.accessible(content,state,_)).map { point =>
      val operations = content.operations.filter(o => o.point == point.id && !state.completedOperations(o.id))
        .map(o => OperationChoiceView(o.id,o.label))
      val routes = content.routes.filter(r => r.point == point.id && state.completedOperations(r.prerequisite) && !r.clues.forall(state.clues.contains))
        .map(r => RouteChoiceView(r.id,r.label))
      InquiryPointView(point.id,point.label,operations,routes)
    }
    val clues = content.clues.filter(c => state.clues(c.id)).map(c => ClueView(c.id,c.title.value,c.text.value))
    val journal = records.filter(_.sequence.value <= state.sequence.value).map(r => InvestigationLogEntry(
      r.id,r.sequence,s"${content.timeOriginLabel} +${r.storyTimeSeconds - content.initialStoryTimeSeconds}秒",r.text,r.disclosedClues))
    InvestigationSnapshot(state.id,state.caseId,state.caseRevision,state.sequence,caseRecord.title,
      StudySceneView("study","1",content.placeName,state.opened(EntityId("drawer")),points.exists(_.id == PointId("point_letter"))),points,clues,journal)
