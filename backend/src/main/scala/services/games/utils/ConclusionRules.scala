package services.games.utils

import services.cases.objects.*
import services.games.objects.*
import services.games.objects.apiTypes.CommandResult
import services.system.objects.*

private[games] object ConclusionRules:
  private[games] def submit(content: CaseContent, state: GameSessionRecord, request: RequestId,
      suspects: Set[PersonId], evidence: Set[ClueId], explanation: String): Either[GameError,ActionPlan] =
    for
      spec <- content.conclusion.toRight(GameError(ErrorCode.AccessDenied,"这份调查尚未开放推理提交。"))
      _ <- Either.cond(spec.evaluation.isEmpty,(),GameError(ErrorCode.AccessDenied,"请先提交推理评审，再单独选择结案或查看解答。"))
      _ <- Either.cond(state.conclusion.isEmpty,(),GameError(ErrorCode.ConditionFailed,"本次推理已经提交，请查看已保存的复盘。"))
      _ <- Either.cond(suspects.nonEmpty && suspects.subsetOf(spec.candidates) && evidence.nonEmpty && evidence.size <= 50 &&
        explanation.trim.length >= 10 && explanation.length <= 1000 && !explanation.exists(c => c < ' ' && c != '\n' && c != '\t'),
        (),GameError(ErrorCode.InvalidValue,"请选择怀疑人物、引用1至50份材料，并填写10至1000字的推理。"))
      _ <- Either.cond(evidence.subsetOf(state.clues),(),GameError(ErrorCode.AccessDenied,"只能引用本次调查实际取得的材料。"))
      point <- content.police.flatMap(id => content.witnesses.find(_.id == id)).map(_.point).toRight(GameError(ErrorCode.AccessDenied,"当前无法提交推理。"))
    yield
      val sequence=Sequence(state.sequence.value+1)
      val suspectScore=if suspects == spec.culprits then 50 else if suspects.subsetOf(spec.culprits) then 25 else 0
      val coverage=spec.evidenceGroups.map(g => ConclusionCoverage(g.label,g.requiredClues,g.requiredClues.subsetOf(evidence)))
      val saved=CaseConclusionRecord(sequence,suspects,evidence,explanation,suspectScore,coverage.count(_.covered)*10,coverage,spec.review)
      val next=state.copy(sequence=sequence,conclusion=Some(saved))
      val text="已保存本次推理与复盘。评分仅检查人物选择和引用材料覆盖，文字论证留作对照；没有执行抓捕或新增案件事实。"
      val record=SessionRecord(RecordId(s"${request.value}:conclusion"),state.id,request,sequence,state.storyTimeSeconds,point,Some(RouteId("submit_conclusion")),None,text,Set.empty,Set.empty)
      ActionPlan(next,record,CommandResult(sequence,Vector(Text(text)),Set.empty))
