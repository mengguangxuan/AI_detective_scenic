import type { WitnessFollowUpView } from '../../../apis/games/objects/WitnessFollowUpView';
import type { OperationId } from '../../../objects/OperationId';
import type { RouteId } from '../../../objects/RouteId';
type Props = { followUp: WitnessFollowUpView; disabled: boolean; operationLabel: string; lastReply: string | undefined;
  onBegin: (operation: OperationId) => void; onQuestion: (route: RouteId) => void };
export default function FollowUpPanel({ followUp, disabled, operationLabel, lastReply, onBegin, onQuestion }: Props) {
  const done = followUp.requirements.filter(item => item.met).length;
  return <section aria-label="补充问询准备" className="rounded-2xl border border-amber-200/25 bg-[#182421] p-5">
    <h3 className="text-lg font-medium">{followUp.title}</h3>
    <p className="mt-2 text-sm leading-7 text-stone-300">{followUp.started ? '补充问询已开始。继续提问并实际记录新陈述，首供保留在线索整理中。' : '先出示相关材料并追问，再开始补充问询。你也可以在下方自由提问。'}</p>
    {!followUp.started ? <><details className="mt-4 text-sm"><summary className="cursor-pointer text-amber-100">查看准备条件（{done}/{followUp.requirements.length}）</summary><ul className="mt-3 space-y-2 text-stone-300">{followUp.requirements.map(item => <li key={item.label}>{item.met ? '✓ 已完成' : '待完成'} · {item.label}</li>)}</ul></details>
      {followUp.questions.length > 0 ? <div className="mt-4 flex flex-wrap gap-3" aria-label="关键证据追问">{followUp.questions.map(question => <button key={question.id} type="button" disabled={disabled} onClick={() => onQuestion(question.id)} className="rounded-lg border border-amber-200/60 px-4 py-2.5 text-left text-sm text-amber-100 disabled:opacity-40">{question.label}</button>)}</div> : null}
      <button type="button" disabled={disabled || !followUp.ready} onClick={() => onBegin(followUp.operation)} className="mt-4 rounded-lg bg-amber-200 px-5 py-2.5 text-sm font-medium text-stone-950 disabled:opacity-40">{operationLabel}</button>
    </> : null}
    {lastReply ? <p className="mt-4 border-l-2 border-amber-200/60 pl-4 text-sm leading-7 text-stone-200">最近固定答复：{lastReply}</p> : null}
  </section>;
}
