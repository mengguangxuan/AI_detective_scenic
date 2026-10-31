package services.games
import services.cases.objects.*
import cats.effect.unsafe.implicits.global
import services.cases.api.ImportCaseMessage
import services.system.api.{DatabaseSession, DatabaseSchema}
import java.nio.file.Paths
import java.util.UUID

// Test-only reconstruction of the preceding published feature set.
private[games] object LegacyFixture:
  private lazy val published: CaseContent =
    val url=s"jdbc:h2:mem:legacy-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
      ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation_v20.json")).plan(c))).unsafeRunSync().content

  def withoutReview(latest: CaseContent): CaseContent =
    val current=published
    current.copy(conclusion=current.conclusion.map(s =>
    s.copy(reviewSections=Vector.empty,review=s.review.replace("已保存的原文、引用和评审反馈保持不变。","已提交的原文、引用和分数保持不变。"))))

  def withoutBeforeCrime(latest: CaseContent): CaseContent =
    val current=withoutReview(latest)
    val clues=Set(ClueId("clue_clement_relationship"),ClueId("clue_clement_early_visit"))
    val routes=current.routes.filter(_.clues.exists(clues)).map(_.id).toSet
    val facts=current.clues.filter(c => clues(c.id)).flatMap(_.facts).toSet
    current.copy(routes=current.routes.filterNot(r => routes(r.id)),clues=current.clues.filterNot(c => clues(c.id)),
      facts=current.facts.filterNot(f => facts(f.id)),witnesses=current.witnesses.map { w =>
        if w.id != WitnessId("clement") then w else w.copy(answers=w.answers.filterNot(_.routes.exists(routes)),
          dossier=w.dossier.copy(knowledge=w.dossier.knowledge.filterNot(k => k.startsWith("周三傍晚，你在画室") || k.startsWith("周四约15:45，你回到寓所")),
            policy=w.dossier.policy.filterNot(_.startsWith("谈及案发前的感情与来访时"))))
      })

  def withoutPlanter(latest: CaseContent): CaseContent =
    val current=withoutBeforeCrime(latest)
    val nodes=Set(SceneNodeId("node_planter"),SceneNodeId("node_planter_interior"))
    val points=Set(PointId("point_planter"),PointId("point_planter_interior"))
    current.copy(nodes=current.nodes.filterNot(n => nodes(n.id)),points=current.points.filterNot(p => points(p.id)),
      operations=current.operations.filterNot(o => points(o.point)),routes=current.routes.filterNot(r => points(r.point)),
      clues=current.clues.filterNot(_.id == ClueId("clue_planter")),facts=current.facts.filterNot(_.id == FactId("fact_planter")),
      sceneObjects=current.sceneObjects.filterNot(o => nodes(o.node)),observations=current.observations.filterNot(o => nodes(o.node)))

  def withoutEvaluation(latest: CaseContent): CaseContent =
    val current=withoutPlanter(latest)
    current.copy(conclusion=current.conclusion.map(_.copy(evaluation=None)))

  def withoutConclusion(current: CaseContent): CaseContent = withoutPlanter(current).copy(conclusion=None)

  def withoutWoodland(latest: CaseContent): CaseContent =
    val current=withoutConclusion(latest)
    val clues=Set("clue_woodland_sample","clue_crystal_opinion","clue_police_woodland_review").map(ClueId.apply)
    val routes=current.routes.filter(_.clues.exists(clues)).map(_.id).toSet
    val facts=current.clues.filter(c => clues(c.id)).flatMap(_.facts).toSet
    current.copy(externalChecks=current.externalChecks.filterNot(c => Set("woodland_search","crystal_opinion")(c.id.value)),
      routes=current.routes.filterNot(r => routes(r.id)),clues=current.clues.filterNot(c => clues(c.id)),
      facts=current.facts.filterNot(f => facts(f.id)),
      witnesses=current.witnesses.map(w => w.copy(answers=w.answers.filterNot(_.routes.exists(routes)))))

  def withoutCallChecks(current: CaseContent): CaseContent =
    val content=withoutWoodland(current)
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
