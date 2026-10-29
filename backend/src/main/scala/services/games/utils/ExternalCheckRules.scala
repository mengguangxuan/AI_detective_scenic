package services.games.utils

import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.*
import services.system.objects.*

// Finite authored checks advance on story time; reading a view never changes the case.
private[games] object ExternalCheckRules:
  private[games] def timeLabel(content: CaseContent, seconds: Long): String =
    val minutes=content.clock.map(_.startMinutesOfDay.toLong).getOrElse(0L)+(seconds-content.initialStoryTimeSeconds)/60
    val days=Vector("周五","周六","周日","周一","周二","周三","周四")
    s"${days(((minutes/1440)%7).toInt)} ${f"${(minutes%1440)/60}%02d"}:${f"${minutes%60}%02d"}"

  private[games] def request(content: CaseContent,state: GameSessionRecord,id: RequestId,checkId: ExternalCheckId): Either[GameError,ActionPlan] =
    for
      spec <- content.externalChecks.find(_.id == checkId).toRight(GameError(ErrorCode.AccessDenied,"没有这项可申请的核查。"))
      _ <- Either.cond(content.points.exists(p => p.id == spec.point && FixtureRules.accessible(content,state,p)),(),GameError(ErrorCode.AccessDenied,"当前无法联系核查人员。"))
      _ <- Either.cond(spec.requiredClues.subsetOf(state.clues),(),GameError(ErrorCode.ConditionFailed,"请先取得申请这项核查所需的材料。"))
      _ <- Either.cond(!state.externalChecks.exists(_.id == checkId),(),GameError(ErrorCode.ConditionFailed,"这项核查已经受理，请查看进展或已有结果。"))
    yield
      val progress=ExternalCheckProgress(checkId,state.storyTimeSeconds,state.storyTimeSeconds+spec.delaySeconds,None)
      val next=state.copy(sequence=Sequence(state.sequence.value+1),externalChecks=state.externalChecks:+progress)
      val text=s"${spec.title}已受理，预计${timeLabel(content,progress.readyAtSeconds)}完成。"
      val record=SessionRecord(RecordId(s"${id.value}:check"),state.id,id,next.sequence,next.storyTimeSeconds,spec.point,Some(RouteId(s"external_check_${checkId.value}")),None,text,Set.empty,Set.empty)
      ActionPlan(next,record,CommandResult(next.sequence,Vector(Text(text)),Set.empty))

  private[games] def waitFor(content: CaseContent,state: GameSessionRecord,id: RequestId,minutes: Int): Either[GameError,ActionPlan] =
    for
      _ <- Either.cond(content.clock.nonEmpty,(),GameError(ErrorCode.ConflictingContent,"这份旧调查尚未启用案件时钟，请保留原存档。"))
      _ <- Either.cond(minutes >= 1 && minutes <= 30,(),GameError(ErrorCode.InvalidValue,"每次可推进1至30分钟。"))
      point <- content.police.map(_.point).toRight(GameError(ErrorCode.AccessDenied,"当前无法整理核查进度。"))
    yield
      val next=state.copy(sequence=Sequence(state.sequence.value+1),storyTimeSeconds=state.storyTimeSeconds+minutes.toLong*60)
      val text=s"整理材料并等待$minutes 分钟，当前${timeLabel(content,next.storyTimeSeconds)}。"
      val record=SessionRecord(RecordId(s"${id.value}:wait"),state.id,id,next.sequence,next.storyTimeSeconds,point,Some(RouteId("advance_time")),None,text,Set.empty,Set.empty)
      ActionPlan(next,record,CommandResult(next.sequence,Vector(Text(text)),Set.empty))

  private[games] def afterAction(content: CaseContent,previous: GameSessionRecord,plan: ActionPlan): ActionPlan =
    content.clock.fold(plan) { clock =>
      val now=if plan.session.storyTimeSeconds == previous.storyTimeSeconds then previous.storyTimeSeconds+clock.actionSeconds else plan.session.storyTimeSeconds
      val due=plan.session.externalChecks.filter(p => p.completedAtSeconds.isEmpty && p.readyAtSeconds <= now)
      val completed=due.map(_.id).toSet
      val results=content.externalChecks.filter(c => completed(c.id)).flatMap(_.resultClues).toSet
      val fresh=content.clues.filter(c => results(c.id) && !plan.session.clues(c.id))
      val texts=fresh.map(c => Text(c.text.value))
      val state=plan.session.copy(storyTimeSeconds=now,
        externalChecks=plan.session.externalChecks.map(p => if completed(p.id) then p.copy(completedAtSeconds=Some(p.readyAtSeconds)) else p),
        clues=plan.session.clues++fresh.map(_.id),facts=plan.session.facts++fresh.flatMap(_.facts))
      val completion=due.flatMap(p => content.externalChecks.find(_.id == p.id).map(c => s"${timeLabel(content,p.readyAtSeconds)} · ${c.title}已完成"))
      val record=plan.record.copy(storyTimeSeconds=now,text=(Vector(plan.record.text)++completion).mkString("\n"),
        disclosedClues=plan.record.disclosedClues++fresh.map(_.id),disclosedFacts=plan.record.disclosedFacts++fresh.flatMap(_.facts))
      plan.copy(session=state,record=record,result=plan.result.copy(findings=plan.result.findings++texts,acquiredClues=plan.result.acquiredClues++fresh.map(_.id)))
    }

  private[games] def views(content: CaseContent,state: GameSessionRecord): Vector[ExternalCheckView] =
    content.externalChecks.map { c =>
      val progress=state.externalChecks.find(_.id == c.id)
      ExternalCheckView(c.id,c.title,c.requestLabel,c.delaySeconds/60,
        progress.isEmpty && c.requiredClues.subsetOf(state.clues) && content.points.exists(p => p.id == c.point && FixtureRules.accessible(content,state,p)),
        progress.map(p => CheckProgressView(timeLabel(content,p.requestedAtSeconds),timeLabel(content,p.readyAtSeconds),p.completedAtSeconds.map(timeLabel(content,_)))))
    }
