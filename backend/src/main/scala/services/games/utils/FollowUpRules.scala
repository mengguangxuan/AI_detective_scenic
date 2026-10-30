package services.games.utils
import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*

private[games] object FollowUpRules:
  private def evidence(content: CaseContent,witness: WitnessId,records: Vector[SessionRecord],turns: Vector[InterviewTurnRecord]): Set[ClueId] =
    val point=content.witnesses.find(_.id == witness).map(_.point)
    val fixed=records.filter(r => point.contains(r.point)).flatMap(_.route).flatMap(id => content.routes.find(_.id == id).toVector.flatMap(_.presentedClues))
    fixed.toSet ++ turns.filter(t => t.witness == witness && (t.mode == InterviewReplyMode.Answered || t.mode == InterviewReplyMode.Unknown)).flatMap(_.shownClues)

  private def responses(content: CaseContent,witness: WitnessId,records: Vector[SessionRecord],turns: Vector[InterviewTurnRecord]): Set[RouteId] =
    val person=content.witnesses.find(_.id == witness)
    val fixed=records.filter(r => person.exists(_.point == r.point)).flatMap(_.route)
    val model=turns.filter(t => t.witness == witness && t.mode == InterviewReplyMode.Answered).flatMap(_.answerIds)
      .flatMap(id => person.toVector.flatMap(_.answers.find(_.id == id).toVector.flatMap(_.routes)))
    fixed.toSet ++ model

  private[games] def permitted(content: CaseContent,state: GameSessionRecord,operation: OperationId,records: Vector[SessionRecord],turns: Vector[InterviewTurnRecord]): Boolean =
    content.followUps.find(_.operation == operation).forall(p =>
      p.requiredEvidence.subsetOf(evidence(content,p.witness,records,turns)) && p.requiredRoutes.subsetOf(responses(content,p.witness,records,turns)))

  private[games] def views(content: CaseContent,state: GameSessionRecord,records: Vector[SessionRecord],turns: Vector[InterviewTurnRecord]): Vector[WitnessFollowUpView] =
    content.followUps.filter(p => content.witnesses.find(_.id == p.witness).exists(w => content.points.exists(point => point.id == w.point && SceneAccessRules.accessible(content,state,point)))).map { p =>
      val shown=evidence(content,p.witness,records,turns)
      val answered=responses(content,p.witness,records,turns)
      val op=content.operations.find(_.id == p.operation).get
      val requirements=p.requiredEvidence.toVector.sortBy(_.value).map { id =>
        FollowUpRequirementView(s"向本人出示：${content.clues.find(_.id == id).get.title.value}",shown(id))
      } ++ p.requiredRoutes.toVector.sortBy(_.value).map { id =>
        FollowUpRequirementView(s"完成追问：${content.routes.find(_.id == id).get.label}",answered(id))
      }
      val questions=content.routes.filter(r => p.requiredRoutes(r.id) && FixtureRules.routeAvailable(state,r) && !r.clues.forall(state.clues.contains)).map(r => RouteChoiceView(r.id,r.label))
      WitnessFollowUpView(p.operation,p.witness,p.title,state.completedOperations(p.operation),
        FixtureRules.operationAvailable(state,op) && permitted(content,state,p.operation,records,turns),requirements,questions)
    }

  private[games] def comparisons(content: CaseContent,state: GameSessionRecord,records: Vector[SessionRecord]): Vector[StatementComparisonView] =
    def version(id: ClueId): Option[StatementVersionView] =
      for
        clue <- content.clues.find(c => c.id == id && state.clues(id))
        record <- records.find(_.disclosedClues(id))
      yield StatementVersionView(ClueView(clue.id,clue.title.value,clue.text.value),record.sequence,
        if content.clock.nonEmpty then ExternalCheckRules.timeLabel(content,record.storyTimeSeconds) else content.timeOriginLabel)
    content.statementComparisons.filter(p => p.rows.exists(r => state.clues(r.initial) || state.clues(r.revised))).map { p =>
      StatementComparisonView(p.witness,p.title,p.rows.map(r => StatementComparisonRowView(r.label,version(r.initial),version(r.revised))),
        Option.when(p.rows.exists(r => state.clues(r.revised)))(p.nextObjective))
    }
