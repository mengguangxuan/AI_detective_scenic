import ReasoningPanel from './components/ReasoningPanel';
import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';
import type { useInvestigation } from '../../features/investigation/hooks/useInvestigation';
import type { ConclusionDraft } from './objects/ConclusionDraft';
import InvestigationLink from '../../app/components/InvestigationLink';
type Props = { snapshot: InvestigationSnapshot; disabled: boolean; draft: ConclusionDraft;
  onChange: (draft: ConclusionDraft) => void; onSubmit: ReturnType<typeof useInvestigation>['conclude']; onEvaluate: ReturnType<typeof useInvestigation>['evaluate']; onReveal: ReturnType<typeof useInvestigation>['revealReasoning'] };
export default function ConclusionPage({ snapshot, disabled, draft, onChange, onSubmit, onEvaluate, onReveal }: Props) {
  if (snapshot.reasoning) return <ReasoningPanel snapshot={snapshot} progress={snapshot.reasoning} disabled={disabled} draft={draft} onChange={onChange} onEvaluate={onEvaluate} onReveal={onReveal}/>;
  const result = snapshot.conclusion;
  return <section aria-labelledby="conclusion-title" className="space-y-5">
    <div><h2 id="conclusion-title" className="text-xl font-medium">推理与复盘</h2><p className="mt-2 text-sm leading-7 text-stone-400">整理你的判断，决定何时提交本次推理。</p></div>
    {!snapshot.conclusionCandidates.length ? <p className="rounded-xl border border-white/15 p-5 text-sm leading-7 text-stone-300">这份旧调查尚未开放推理提交，原进度仍可继续。体验本功能需另建当前修订的调查。</p> : result ?
      <section aria-label="已保存的推理与复盘" className="space-y-5 rounded-xl border border-amber-200/25 p-5">
        <div><h3 className="text-lg text-amber-100">本次推理已保存 · {result.suspectScore + result.evidenceScore} / 100</h3>
          <p className="mt-2 text-sm leading-7 text-stone-300">人物选择 {result.suspectScore} / 50 · 引用覆盖 {result.evidenceScore} / 50。文字论证未自动判分；分数不代表证据已证明全部经过。</p></div>
        <div><h3 className="font-medium">你的怀疑对象</h3><p className="mt-2 text-sm text-stone-300">{result.suspects.map(id => snapshot.people.find(p => p.id === id)?.name ?? id).join('、')}</p></div>
        <div><h3 className="font-medium">你的推理原文</h3><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-7 text-stone-300">{result.explanation}</p></div>
        <div><h3 className="font-medium">本次引用的材料</h3><ul className="mt-2 space-y-1 text-sm text-stone-300">{result.evidence.map(id => <li key={id}>{snapshot.clues.find(c => c.id === id)?.title ?? id}</li>)}</ul></div>
        <div><h3 className="font-medium">引用覆盖对照</h3><p className="mt-2 text-xs leading-6 text-stone-400">每组10分，须在本次提交中引用组内全部材料；取得但未引用不计入。这里只核对材料覆盖，不判断论证是否成立。</p>
          <ul className="mt-3 space-y-3">{result.coverage.map(g => <li key={g.label} className="text-sm leading-7"><p>{g.label} · {g.covered ? '已覆盖 +10' : '未覆盖 +0'}</p><p className="text-stone-400">{g.requiredClues.map(id => snapshot.clues.find(c => c.id === id)?.title ?? '尚未取得的材料').join('、')}</p></li>)}</ul></div>
        <p className="whitespace-pre-wrap break-words border-t border-white/15 pt-5 text-sm leading-7 text-stone-300">{result.review}</p>
        <p className="text-xs leading-6 text-stone-400">本次提交不再覆盖。你仍可返回调查，新取得的材料不会改写这次分数。</p>
      </section> : <form aria-label="推理提交" className="space-y-5 rounded-xl border border-white/15 p-5" onSubmit={event => { event.preventDefault(); if (draft.reveal && !disabled) onSubmit(draft.suspects, draft.evidence, draft.explanation); }}>
        <p className="text-sm leading-7 text-amber-100">提交后会显示包含核心解答的完整复盘。每局保存一次推理；不要求点完全部问答，也可以继续调查后再来。</p>
        <fieldset disabled={disabled}><legend className="mb-3 text-sm font-medium">怀疑人物（可多选）</legend><div className="grid gap-3 sm:grid-cols-2">{snapshot.people.filter(p => snapshot.conclusionCandidates.includes(p.id)).map(p => <label key={p.id} className="flex items-start gap-2 text-sm"><input type="checkbox" checked={draft.suspects.includes(p.id)} onChange={e => onChange({ ...draft, suspects: e.target.checked ? [...draft.suspects, p.id] : draft.suspects.filter(id => id !== p.id) })}/>{p.name}</label>)}</div></fieldset>
        <fieldset disabled={disabled}><legend className="mb-3 text-sm font-medium">引用材料（1至50份）</legend>{!snapshot.clues.length ? <p className="text-sm text-stone-400">尚未取得材料，请先调查。</p> : <div className="grid gap-3 sm:grid-cols-2">{snapshot.clues.map(c => <label key={c.id} className="flex items-start gap-2 text-sm"><input type="checkbox" checked={draft.evidence.includes(c.id)} onChange={e => onChange({ ...draft, evidence: e.target.checked ? [...draft.evidence, c.id] : draft.evidence.filter(id => id !== c.id) })}/>{c.title}</label>)}</div>}</fieldset>
        <div><label htmlFor="conclusion-explanation" className="block text-sm">你的推理（动机、经过与材料之间的联系）</label><textarea id="conclusion-explanation" value={draft.explanation} disabled={disabled} minLength={10} maxLength={1000} required rows={7} onChange={e => onChange({ ...draft, explanation: e.target.value })} className="mt-3 block w-full rounded-lg border border-white/20 bg-[#101918] p-3 leading-7"/></div>
        <p className="text-xs leading-6 text-stone-400">评分检查人物选择（50分）与五组材料的引用覆盖（各10分），不会自动理解或判分自由文字。原文会保存供你与复盘对照。</p>
        <label className="flex items-start gap-2 text-sm leading-6"><input type="checkbox" disabled={disabled} checked={draft.reveal} onChange={e => onChange({ ...draft, reveal: e.target.checked })}/>我已完成本次推理，同意显示完整复盘</label>
        <button type="submit" disabled={disabled || !draft.reveal || !draft.suspects.length || !draft.evidence.length || draft.evidence.length > 50 || draft.explanation.trim().length < 10} className="rounded-lg border border-amber-200 px-4 py-2 text-sm text-amber-100 disabled:opacity-40">提交推理并查看复盘</button>
      </form>}
    <InvestigationLink href="/clues" className="inline-block text-sm text-amber-200 underline underline-offset-4">返回线索整理</InvestigationLink>
  </section>;
}
