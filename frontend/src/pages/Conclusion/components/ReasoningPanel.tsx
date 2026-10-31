import type { InvestigationSnapshot } from '../../../apis/games/objects/InvestigationSnapshot';
import type { ReasoningProgress } from '../../../apis/games/objects/ReasoningProgress';
import type { useInvestigation } from '../../../features/investigation/hooks/useInvestigation';
import type { ConclusionDraft } from '../objects/ConclusionDraft';
import InvestigationLink from '../../../app/components/InvestigationLink';
type Props = { snapshot: InvestigationSnapshot; progress: ReasoningProgress; disabled: boolean; draft: ConclusionDraft;
  onChange: (draft: ConclusionDraft) => void; onEvaluate: ReturnType<typeof useInvestigation>['evaluate']; onReveal: ReturnType<typeof useInvestigation>['revealReasoning'] };
const statusText = { Revise: '继续推敲', Established: '核心推理成立', Unavailable: '评审暂不可用', Rejected: '评审未完成' } as const;
export default function ReasoningPanel({ snapshot, progress, disabled, draft, onChange, onEvaluate, onReveal }: Props) {
  const established = progress.attempts.at(-1)?.status === 'Established';
  return <section aria-labelledby="reasoning-title" className="space-y-5">
    <div><h2 id="reasoning-title" className="text-xl font-medium">推理与复盘</h2><p className="mt-2 text-sm leading-7 text-stone-400">提交你的解释，依据反馈继续调查、修改和论证。评审不会自动展示完整解答。</p></div>
    <section aria-label="推理评审记录" className="space-y-4">
      {!progress.attempts.length ? <p className="text-sm text-stone-400">尚未提交推理。先取得材料，再写下你的判断。</p> : progress.attempts.map((a, index) => <details key={a.requestId} open={index === progress.attempts.length - 1} className="rounded-xl border border-white/15 p-5">
        <summary className="cursor-pointer text-amber-100">第{index + 1}次推理 · {statusText[a.status]}</summary>
        <p className="mt-4 whitespace-pre-wrap text-sm leading-7" role="status">{a.feedback}</p>
        {a.checks.length ? <ul className="mt-3 space-y-1 text-sm text-stone-400">{a.checks.map(c => <li key={c.id}>{c.label} · {c.satisfied ? '已有支持' : '仍需论证'}</li>)}</ul> : null}
        <p className="mt-4 text-sm text-stone-400">怀疑人物：{a.suspects.map(id => snapshot.people.find(p => p.id === id)?.name ?? id).join('、')}</p>
        <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-7 text-stone-300">{a.explanation}</p>
        <p className="mt-2 text-sm leading-7 text-stone-400">引用：{a.evidence.map(id => snapshot.clues.find(c => c.id === id)?.title ?? id).join('、')}</p>
        {!progress.ending && !established ? <button type="button" disabled={disabled} onClick={() => onChange({ suspects: [...a.suspects], evidence: [...a.evidence], explanation: a.explanation, reveal: false })} className="mt-3 rounded-lg border border-white/25 px-3 py-2 text-sm disabled:opacity-40">以第{index + 1}次推理继续修改</button> : null}
      </details>)}
    </section>
    {progress.ending ? <section aria-label="本案收尾与复盘" className="rounded-xl border border-amber-200/25 p-5">
      <h3 className="text-lg text-amber-100">{progress.ending.mode === 'Solved' ? '已记录推理成立后的结案' : '已主动查看解答 · 本次未计为破案'}</h3>
      <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-7 text-stone-300">{progress.ending.review}</p>
    </section> : established ? <section aria-label="结案选择" className="rounded-xl border border-amber-200/25 p-5">
      <p className="text-sm leading-7">本次核心解释已通过评审。你可以继续调查，或确认结案后查看完整复盘。</p>
      <button type="button" disabled={disabled} onClick={() => onReveal(false)} className="mt-4 rounded-lg border border-amber-200 px-4 py-2 text-sm disabled:opacity-40">结案并查看复盘</button>
    </section> : <>
      <form aria-label="推理评审" onSubmit={e => { e.preventDefault(); if (!disabled) onEvaluate(draft.suspects, draft.evidence, draft.explanation); }} className="space-y-5 rounded-xl border border-white/15 p-5">
        <fieldset disabled={disabled}><legend className="mb-3 text-sm">怀疑人物（可多选）</legend><div className="grid gap-3 sm:grid-cols-2">{snapshot.people.filter(p => snapshot.conclusionCandidates.includes(p.id)).map(p => <label key={p.id} className="flex items-start gap-2 text-sm"><input type="checkbox" checked={draft.suspects.includes(p.id)} onChange={e => onChange({ ...draft, suspects: e.target.checked ? [...draft.suspects, p.id] : draft.suspects.filter(id => id !== p.id) })}/>{p.name}</label>)}</div></fieldset>
        <fieldset disabled={disabled}><legend className="mb-3 text-sm">引用材料（1至50份）</legend>{!snapshot.clues.length ? <p className="text-sm text-stone-400">请先取得调查材料。</p> : <div className="grid gap-3 sm:grid-cols-2">{snapshot.clues.map(c => <label key={c.id} className="flex items-start gap-2 text-sm"><input type="checkbox" checked={draft.evidence.includes(c.id)} onChange={e => onChange({ ...draft, evidence: e.target.checked ? [...draft.evidence, c.id] : draft.evidence.filter(id => id !== c.id) })}/>{c.title}</label>)}</div>}</fieldset>
        <div><label htmlFor="reasoning-explanation" className="text-sm">你的推理（人物责任、动机、经过与证据之间的联系）</label><textarea id="reasoning-explanation" disabled={disabled} required minLength={10} maxLength={1000} rows={7} value={draft.explanation} onChange={e => onChange({ ...draft, explanation: e.target.value })} className="mt-3 block w-full rounded-lg border border-white/20 bg-[#101918] p-3 text-sm leading-7"/></div>
        <p className="text-xs leading-6 text-stone-400">允许多次评审，每次提交和反馈分别保留。评审是对现有解释的辅助判断，不代替你对证据的核实。</p>
        <button type="submit" disabled={disabled || !draft.suspects.length || !draft.evidence.length || draft.evidence.length > 50 || draft.explanation.trim().length < 10} className="rounded-lg border border-amber-200 px-4 py-2 text-sm text-amber-100 disabled:opacity-40">提交推理评审</button>
      </form>
      <details className="rounded-xl border border-white/15 p-5"><summary className="cursor-pointer text-sm text-stone-400">提前查看作者解答（含剧透）</summary>
        <p className="mt-3 text-sm leading-7 text-stone-400">这会结束本次评审并揭示完整解答，不计为推理破案。</p>
        <label className="mt-3 flex items-start gap-2 text-sm leading-6"><input type="checkbox" checked={draft.reveal} disabled={disabled} onChange={e => onChange({ ...draft, reveal: e.target.checked })}/>我选择放弃本次推理并查看完整解答</label>
        <button type="button" disabled={disabled || !draft.reveal} onClick={() => onReveal(true)} className="mt-4 rounded-lg border border-white/25 px-4 py-2 text-sm disabled:opacity-40">放弃推理并查看解答</button>
      </details>
    </>}
    <InvestigationLink href="/clues" className="inline-block text-sm text-amber-200 underline underline-offset-4">返回线索整理继续调查</InvestigationLink>
  </section>;
}
