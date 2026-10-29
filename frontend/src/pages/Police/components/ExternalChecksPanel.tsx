import type { InvestigationSnapshot } from '../../../apis/games/objects/InvestigationSnapshot';
import type { ExternalCheckId } from '../../../objects/ExternalCheckId';
type Props = { checks: InvestigationSnapshot['externalChecks']; disabled: boolean; onRequest: (id: ExternalCheckId) => void };
export default function ExternalChecksPanel({ checks, disabled, onRequest }: Props) {
  if (checks.length === 0) return null;
  return <section aria-label="核查进展" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <h3 className="text-lg font-medium">核查进展</h3><p className="mt-2 text-sm leading-7 text-stone-400">受理后可继续调查，也可以整理材料等待。到预计时间，结果会进入线索整理与调查日志。</p>
    <div className="mt-4 grid gap-4 md:grid-cols-2">{checks.map(check => <article key={check.id} aria-label={check.title} className="rounded-xl border border-white/15 p-4">
      <h4 className="font-medium text-amber-100">{check.title}</h4>
      {check.progress ? <div className="mt-3 text-sm leading-7 text-stone-300"><p>受理：{check.progress.requestedAt}</p>{check.progress.completedAt ? <p role="status">已完成 · {check.progress.completedAt}，请到线索整理查看结果。</p> : <p role="status">办理中 · 预计 {check.progress.readyAt} 完成</p>}</div> : <><p className="mt-3 text-sm text-stone-400">预计需 {check.delayMinutes} 分钟</p><button type="button" disabled={disabled || !check.available} onClick={() => onRequest(check.id)} className="mt-4 rounded-lg border border-amber-200/60 px-4 py-2 text-sm text-amber-100 disabled:opacity-40">{check.requestLabel}</button></>}
    </article>)}</div>
  </section>;
}
