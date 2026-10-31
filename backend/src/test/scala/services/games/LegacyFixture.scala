package services.games
import services.cases.objects.*

// Test-only reconstruction of the preceding published feature set.
private[games] object LegacyFixture:
  def withoutCallChecks(content: CaseContent): CaseContent =
    val clues=Set("clue_call_basis","clue_call_origin","clue_call_lodge_scene","clue_call_lodge_phone","clue_clement_call_origin_reply","clue_redding_call_origin_reply","clue_police_call_review").map(ClueId.apply)
    val routes=content.routes.filter(_.clues.exists(clues)).map(_.id).toSet
    val facts=content.clues.filter(c => clues(c.id)).flatMap(_.facts).toSet
    content.copy(externalChecks=content.externalChecks.filterNot(c => Set("call_origin","call_lodge_inspection")(c.id.value)),
      routes=content.routes.filterNot(r => routes(r.id)),clues=content.clues.filterNot(c => clues(c.id)),
      facts=content.facts.filterNot(f => facts(f.id)),
      witnesses=content.witnesses.map(w => w.copy(answers=w.answers.filterNot(_.routes.exists(routes)))))

  def withoutSightline(current: CaseContent): CaseContent =
    val content=withoutCallChecks(current)
    val clues=Set("clue_sightline_basis","clue_sightline_recheck","clue_anne_sightline_reply","clue_marple_sightline_reply","clue_police_sightline_review").map(ClueId.apply)
    val routes=content.routes.filter(_.clues.exists(clues)).map(_.id).toSet
    val facts=content.clues.filter(c => clues(c.id)).flatMap(_.facts).toSet
    content.copy(externalChecks=content.externalChecks.filterNot(_.id == ExternalCheckId("sightline_recheck")),
      routes=content.routes.filterNot(r => routes(r.id)),clues=content.clues.filterNot(c => clues(c.id)),
      facts=content.facts.filterNot(f => facts(f.id)),
      witnesses=content.witnesses.map(w => w.copy(answers=w.answers.filterNot(_.routes.exists(routes)))))

  def withoutPaperChecks(current: CaseContent): CaseContent =
    val content=withoutSightline(current)
    val clues=Set("clue_paper_ink","clue_paper_fingerprints","clue_paper_handwriting","clue_police_paper_review").map(ClueId.apply)
    val facts=content.clues.filter(c => clues(c.id)).flatMap(_.facts).toSet
    val route=RouteId("route_police_paper_review")
    content.copy(externalChecks=content.externalChecks.filterNot(c => Set("paper_inspection","paper_handwriting")(c.id.value)),
      routes=content.routes.filterNot(_.id == route),clues=content.clues.filterNot(c => clues(c.id)),
      facts=content.facts.filterNot(f => facts(f.id)),
      witnesses=content.witnesses.map(w => w.copy(answers=w.answers.filterNot(_.routes.contains(route)))))

  def withoutArcher(current: CaseContent): CaseContent =
    val content=withoutPaperChecks(current)
    val routes=Set("route_archer_gun_memory","route_archer_household","route_police_archer_review").map(RouteId.apply)
    val clues=content.routes.filter(r => routes(r.id)).flatMap(_.clues).toSet
    val facts=content.clues.filter(c => clues(c.id)).flatMap(_.facts).toSet
    content.copy(
      nodes=content.nodes.filterNot(_.id == SceneNodeId("node_archer_meeting")),
      points=content.points.filterNot(_.id == PointId("point_archer")),
      operations=content.operations.filterNot(_.id == OperationId("open_archer_meeting")),
      routes=content.routes.filterNot(r => routes(r.id)),
      clues=content.clues.filterNot(c => clues(c.id)),facts=content.facts.filterNot(f => facts(f.id)),
      witnesses=content.witnesses.filterNot(_.id == WitnessId("archer")).map(w => w.copy(answers=w.answers.filterNot(a => a.routes.exists(routes)))),
      publicPersons=content.publicPersons.filterNot(_.id == PersonId("mrs_archer")))

  def withoutFollowUps(current: CaseContent): CaseContent =
    val content=withoutArcher(current)
    val operations=content.followUps.map(_.operation).toSet
    val added=content.routes.filter(r => r.prerequisite.exists(operations) ||
      Set("route_redding_time_challenge","route_anne_gun_challenge","route_anne_redding_challenge","route_police_revised_review")(r.id.value))
    val routes=added.map(_.id).toSet
    val clues=added.flatMap(_.clues).toSet
    content.copy(followUps=Vector.empty,statementComparisons=Vector.empty,
      operations=content.operations.filterNot(o => operations(o.id)),
      routes=content.routes.filterNot(r => routes(r.id)).map(_.copy(disabledAfter=None)),
      clues=content.clues.filterNot(c => clues(c.id)),
      witnesses=content.witnesses.map(w => w.copy(answers=w.answers.filterNot(a => a.routes.exists(routes)))))
