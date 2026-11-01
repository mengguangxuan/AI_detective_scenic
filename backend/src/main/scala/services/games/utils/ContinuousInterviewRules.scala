package services.games.utils

import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.CommandResult
import services.system.objects.*

private[games] object ContinuousInterviewRules:
  private def denied=GameError(ErrorCode.ConditionFailed,"请先取得并实际出示相关材料，再询问本人。")

  private[games] def fixed(content: CaseContent,state: GameSessionRecord,requestId: RequestId,point: PointId,routeId: RouteId,
      records: Vector[SessionRecord],turns: Vector[InterviewTurnRecord]): Either[GameError,(ActionPlan,InterviewTurnRecord)] =
    for
      person <- content.witnesses.find(w => w.point == point && content.continuousInterviews.exists(_.witness == w.id)).toRight(denied)
      _ <- content.points.find(_.id == point).filter(SceneAccessRules.accessible(content,state,_)).toRight(denied)
      route <- content.routes.find(r => r.id == routeId && r.point == point).toRight(denied)
      _ <- Either.cond(FixtureRules.routeAvailable(state,route) && FixtureRules.routePending(content,state,route,records,turns),(),denied)
      context <- InterviewRules.context(content,state,person.id,route.label,route.presentedClues,turns,records)
      answer <- context.profile.answers.filter(_.routes.contains(route.id)).sortBy(_.routes.size).headOption.toRight(denied)
      text=route.reply.getOrElse(answer.text)
      basis=route.presentedClues ++ route.requiredClues.intersect(context.recordedStatements.map(_.id).toSet)
      updates=if context.statementAnswers(answer.id) then Vector(InterviewStatementUpdate(answer.id,basis,text)) else Vector.empty
      decision=InterviewDecision(Vector(answer.id),Some(text),updates)
      _ <- Either.cond(InterviewRules.validDecision(decision,context),(),denied)
      request=InterviewRequestRecord(state.id,requestId,"fixed",state.sequence,person.id,route.label,route.presentedClues,context,"fixed",0L,InterviewRequestStatus.Pending)
      planned <- InterviewRules.plan(content,state,request,Some(decision),None)
    yield
      val (action,turn)=planned
      (action.copy(record=action.record.copy(route=Some(route.id),text=s"问询${person.name}：${route.label}\n$text")),turn)
