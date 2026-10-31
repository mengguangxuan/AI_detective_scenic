import FollowUpPanel from './components/FollowUpPanel';
import type { InvestigationPageState } from '../../features/investigation/objects/InvestigationPageState';
import type { useInvestigation } from '../../features/investigation/hooks/useInvestigation';
import ConversationPanel from '../../features/investigation/components/ConversationPanel';
import InterviewPanel from './components/InterviewPanel';
type Props = { ready: Extract<InvestigationPageState, { kind: 'ready' }>;
  actions: Pick<ReturnType<typeof useInvestigation>, 'select' | 'execute' | 'investigate' | 'lastReply' | 'ask'> };
export default function InterviewsPage({ ready, actions }: Props) {
  const points = ready.snapshot.points.filter(p => p.id !== ready.snapshot.police?.point && ready.snapshot.witnesses.some(w => w.point === p.id));
  const selected = points.find(p => ready.selected.kind === 'point' && p.id === ready.selected.id);
  const witness = ready.snapshot.witnesses.find(w => w.point === selected?.id);
  const followUp = ready.snapshot.followUps.find(item => item.witness === witness?.id);
  const phaseOperations = new Set(ready.snapshot.followUps.map(item => item.operation));
  const primaryQuestions = new Set(ready.snapshot.followUps.flatMap(item => item.questions.map(question => question.id)));
  const backupPoints = points.map(point => ({ ...point, operations: point.operations.filter(operation => !phaseOperations.has(operation.id)), routes: point.routes.filter(route => !primaryQuestions.has(route.id)) }));
  return <section aria-labelledby="interviews-page-title" className="space-y-5">
    <div><h2 id="interviews-page-title" className="text-xl font-medium">人物问询</h2><p className="mt-2 text-sm leading-6 text-stone-400">选择谈话对象，了解各自的经历，也可以出示已经取得的材料继续追问。</p></div>
    <div className="flex flex-wrap gap-2" aria-label="自由问询对象">{ready.snapshot.witnesses.filter(w => points.some(p => p.id === w.point)).map(w => <button key={w.id} type="button" aria-pressed={w.id === witness?.id} onClick={() => actions.select(w.point)} className="rounded-lg border border-white/25 px-4 py-2 text-sm hover:bg-white/10 aria-pressed:border-amber-200 aria-pressed:text-amber-100">与{w.name}交谈</button>)}</div>
    {followUp && selected ? <FollowUpPanel followUp={followUp} disabled={ready.mutation.kind !== 'idle'} operationLabel={selected.operations.find(item => item.id === followUp.operation)?.label ?? '开始补充问询'} lastReply={actions.lastReply?.point === selected.id ? actions.lastReply.text : undefined}
      onBegin={operation => actions.execute(operation, selected.id)} onQuestion={route => actions.investigate(route, selected.id)}/> : null}
    {witness ? <ConversationPanel key={`${ready.snapshot.sessionId}:${witness.id}`} witness={witness} clues={ready.snapshot.clues} turns={ready.snapshot.interviews.filter(t => t.witness === witness.id)} disabled={ready.mutation.kind !== 'idle'} onAsk={actions.ask}/> : <p className="text-sm text-stone-400">选择一位人物，开始自由提问。</p>}
    {selected && selected.presentations.length > 0 ? <section aria-label="已出示的材料" className="rounded-2xl border border-white/10 bg-[#182421] p-5"><h3 className="font-medium">与{witness?.name}的材料出示记录</h3>{selected.presentations.map(item => <article key={item.sequence} className="mt-4 border-l-2 border-amber-200/60 pl-4"><p className="text-xs text-stone-400">出示：{item.evidence.map(id => ready.snapshot.clues.find(clue => clue.id === id)?.title ?? id).join('、')}</p><p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-stone-200">{item.reply}</p></article>)}</section> : null}
    <details className="rounded-2xl border border-white/10 bg-[#182421] p-4"><summary className="cursor-pointer text-sm text-stone-300">固定问答与材料笔记（备用）</summary><div className="mt-4 space-y-4">
    <InterviewPanel title="当面询问" description="选择谈话对象。" points={backupPoints} selected={selected?.id ?? null}
      disabled={ready.mutation.kind !== 'idle'} reply={actions.lastReply} onSelect={actions.select} onExecute={actions.execute} onInvestigate={actions.investigate}/>
    {selected ? <section aria-label="已记录的答复" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
      <h3 className="font-medium">{selected.label} · 已记录的答复</h3>
      {selected.findings.length ? selected.findings.map(finding => <article key={finding.id} className="mt-4 border-l-2 border-amber-200/60 pl-4"><h4 className="text-xs text-stone-400">{finding.title}</h4><p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-stone-200">{finding.text}</p></article>) : <p className="mt-3 text-sm text-stone-400">尚未记录这位人物的答复。</p>}
    </section> : null}
    </div></details>
  </section>;
}
