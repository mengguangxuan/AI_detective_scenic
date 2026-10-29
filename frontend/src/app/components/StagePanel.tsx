import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';

export default function StagePanel({ stage, compact = false }: { stage: InvestigationSnapshot['stage']; compact?: boolean }) {
  if (!stage) return null;
  return <section aria-label="阶段目标" className="mb-5 rounded-xl border border-amber-200/25 bg-amber-200/5 p-5 text-sm leading-7 text-stone-300">
    <h2 className="text-base font-medium text-amber-100">{stage.completed ? '阶段完成 · 书房初查' : '当前阶段 · 书房初查'}</h2>
    {!compact ? <p className="mt-2">{stage.introduction}</p> : null}
    {!compact || stage.completed ? <p className="mt-2">{stage.completed ? stage.nextObjective ?? '你的初查笔记已保存。本次演示到此，后续调查尚未开放。' : stage.objective}</p> : null}
  </section>;
}
