package services.games
import services.cases.objects.*

// Test-only reconstruction of the preceding published feature set.
private[games] object LegacyFixture:
  def withoutFollowUps(content: CaseContent): CaseContent =
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
