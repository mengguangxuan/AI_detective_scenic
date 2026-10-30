import ExternalChecksPanel from './components/ExternalChecksPanel';
import { useState, type FormEvent } from 'react';
import type { InvestigationPageState } from '../../features/investigation/objects/InvestigationPageState';
import type { useInvestigation } from '../../features/investigation/hooks/useInvestigation';
import type { ClueId } from '../../objects/ClueId';
import ConversationPanel from '../../features/investigation/components/ConversationPanel';
import InvestigationLink from '../../app/components/InvestigationLink';

type Props = { ready: Extract<InvestigationPageState, { kind: 'ready' }>;
  actions: Pick<ReturnType<typeof useInvestigation>, 'investigate' | 'report' | 'ask' | 'execute' | 'requestCheck'> };
export default function PolicePage({ ready, actions }: Props) {
  const [selected, setSelected] = useState<readonly ClueId[]>([]);
  const police = ready.snapshot.police;
  const point = ready.snapshot.points.find(p => p.id === police?.point);
  if (!police || !point) return <section><h2 className="text-xl">警方交流</h2><p className="mt-3 text-sm text-stone-400">当前没有可接触的警方人员。</p></section>;
  const witness = ready.snapshot.witnesses.find(w => w.point === police.point);
  const disabled = ready.mutation.kind !== 'idle';
  const available = ready.snapshot.clues.filter(clue => !police.sharedClues.includes(clue.id));
  const evidence = selected.filter(id => available.some(clue => clue.id === id));
  const contacted = ready.snapshot.witnesses.filter(w => ['valet', 'stone', 'hartnell', 'archer'].includes(w.id));
  const titles = new Map(ready.snapshot.clues.map(clue => [clue.id, clue.title]));
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!disabled && evidence.length > 0 && evidence.length <= 20) actions.report(evidence);
  }
  return <section aria-labelledby="police-page-title" className="space-y-5">
    <div><h2 id="police-page-title" className="text-xl font-medium">警方交流</h2><p className="mt-2 text-sm leading-7 text-stone-400">询问警方掌握的情况，交换你已取得的材料。</p></div>
    <ExternalChecksPanel checks={ready.snapshot.externalChecks} disabled={disabled} onRequest={actions.requestCheck}/>
    {point.operations.length ? <section aria-label="调查安排" className="rounded-2xl border border-amber-200/25 bg-[#182421] p-5"><h3 className="text-lg font-medium">调查安排</h3><p className="mt-2 text-sm leading-7 text-stone-400">联系相关人物后，可前往人物问询取得证言，再把材料出示给警方。安排本身也会保存到调查记录。</p><div className="mt-4 flex flex-wrap gap-3">{point.operations.map(operation => <button key={operation.id} type="button" disabled={disabled} onClick={() => actions.execute(operation.id, point.id)} className="rounded-lg bg-amber-200 px-5 py-2.5 text-sm font-medium text-stone-950 disabled:opacity-40">{operation.label}</button>)}</div></section> : null}
    {ready.snapshot.witnesses.some(w => w.id === 'anne') ? <InvestigationLink href="/interviews" className="inline-block text-sm text-amber-200 underline underline-offset-4">前往人物问询，与安妮交谈</InvestigationLink> : null}
    {contacted.length > 0 ? <section aria-label="已联系的核查对象" className="rounded-2xl border border-white/10 bg-[#182421] p-5"><h3 className="font-medium">已联系的核查对象</h3><p className="mt-2 text-sm leading-7 text-stone-300">{contacted.map(w => w.name).join('、')}。会面已安排，证言需实际问询后取得。</p><InvestigationLink href="/interviews" className="mt-3 inline-block text-sm text-amber-200 underline underline-offset-4">前往人物问询，核查证言</InvestigationLink></section> : null}
    {witness ? <ConversationPanel key={`${ready.snapshot.sessionId}:${witness.id}`} witness={witness} clues={ready.snapshot.clues} turns={ready.snapshot.interviews.filter(t => t.witness === witness.id)} disabled={disabled} onAsk={actions.ask}/> : null}
    <details className="rounded-2xl border border-white/10 bg-[#182421] p-4"><summary className="cursor-pointer text-sm text-stone-300">固定问答与材料登记（备用）</summary><div className="mt-4 space-y-5">
    <section aria-label="向警方了解情况" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
      <h3 className="text-lg font-medium">与{police.name}交谈</h3><p className="mt-2 text-sm leading-7 text-stone-400">{police.background}</p>
      <div className="mt-5 flex flex-wrap gap-3">{point.routes.map(route => <button key={route.id} type="button" disabled={disabled}
        onClick={() => actions.investigate(route.id, point.id)}
        className="rounded-lg border border-amber-200/60 px-4 py-2.5 text-sm text-amber-100 hover:bg-amber-200/10 disabled:opacity-40">{route.label}</button>)}</div>
      <div className="mt-6 space-y-5">{point.findings.map(finding => <article key={finding.id} className="border-l-2 border-amber-200/60 pl-4"><h4 className="text-sm font-medium text-amber-100">{finding.title}</h4><p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-stone-200">{finding.text}</p></article>)}</div>
    </section>
    <section aria-label="向警方出示材料" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
      <h3 className="text-lg font-medium">出示调查材料</h3><p className="mt-2 text-sm leading-7 text-stone-400">已向警方出示 {police.sharedClues.length} 份。取得材料后，选择要交流的内容。</p>
      {available.length ? <form onSubmit={submit} className="mt-4 space-y-4"><fieldset disabled={disabled} className="grid gap-3 sm:grid-cols-2"><legend className="mb-3 text-sm text-stone-300">本次出示的材料（最多20份）</legend>{available.map(clue => <label key={clue.id} className="flex items-start gap-3 text-sm leading-6"><input type="checkbox" checked={evidence.includes(clue.id)}
        onChange={event => setSelected(event.target.checked ? [...evidence, clue.id] : evidence.filter(id => id !== clue.id))} className="mt-1 accent-amber-200"/>{clue.title}</label>)}</fieldset>
        <button type="submit" disabled={disabled || evidence.length === 0 || evidence.length > 20} className="rounded-lg bg-amber-200 px-5 py-2.5 text-sm font-medium text-stone-950 disabled:opacity-40">向警方出示</button></form>
        : <p className="mt-4 text-sm leading-7 text-stone-400">{ready.snapshot.clues.length ? '手中的材料都已向警方出示。' : '还没有取得材料，可以先检查现场或向警方了解情况。'}</p>}
      {police.sharedClues.length ? <p className="mt-5 border-t border-white/10 pt-4 text-sm leading-7 text-stone-300">已出示：{police.sharedClues.map(id => titles.get(id)).join('、')}</p> : null}
    </section>
    <section aria-label="警方交流记录" className="rounded-2xl border border-white/10 bg-[#182421] p-5"><h3 className="text-lg font-medium">交流记录</h3>
      {police.exchanges.length ? <ol className="mt-5 space-y-5">{police.exchanges.map(exchange => <li key={exchange.requestId} className="border-l-2 border-white/20 pl-4"><p className="text-xs leading-6 text-stone-400">你出示了：{exchange.evidence.map(id => titles.get(id)).join('、')}</p><p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-stone-200">{exchange.reply}</p></li>)}</ol> : <p className="mt-3 text-sm text-stone-400">还没有向警方出示材料。</p>}
    </section>
    </div></details>
    <InvestigationLink href="/journal" className="inline-block text-sm text-amber-200 underline underline-offset-4">查看完整调查日志</InvestigationLink>
  </section>;
}
