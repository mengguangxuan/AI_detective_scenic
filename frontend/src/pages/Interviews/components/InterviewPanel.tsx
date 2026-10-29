import type { InquiryPointView } from '../../../apis/games/objects/InquiryPointView';
import type { PointId } from '../../../objects/PointId';
import type { OperationId } from '../../../objects/OperationId';
import type { RouteId } from '../../../objects/RouteId';
type Props = { points: readonly InquiryPointView[]; selected: PointId | null; disabled: boolean;
  title: string; description: string; emptyMessage?: string; reply?: { point: PointId; text: string } | null;
  onSelect: (point: PointId) => void; onExecute: (operation: OperationId) => void; onInvestigate: (route: RouteId) => void };
export default function InterviewPanel({ points, selected, disabled, title, description, emptyMessage, reply, onSelect, onExecute, onInvestigate }: Props) {
  const point = points.find(p => p.id === selected);
  return <section aria-label={title} className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <h2 className="text-lg font-medium">{title}</h2>
    <p className="mt-1 text-sm leading-6 text-stone-400">{description}</p>
    <div className="mt-5 flex flex-wrap gap-2">{points.map(p => <button key={p.id} type="button" aria-pressed={selected === p.id} onClick={() => onSelect(p.id)} className="rounded-lg border border-stone-500 px-4 py-2 hover:bg-white/10 aria-pressed:border-amber-300 aria-pressed:text-amber-200">{p.label}</button>)}</div>
    <div className="mt-5 min-h-28 border-t border-white/10 pt-4">
      {!point ? <p className="text-sm text-stone-400">选择一位谈话对象。</p> : <>
        <h3 className="text-sm text-stone-300">{point.label}</h3>
        <div className="mt-4 flex flex-wrap gap-2">
          {point.operations.map(operation => <button key={operation.id} type="button" disabled={disabled} onClick={() => onExecute(operation.id)} className="rounded-lg bg-amber-200 px-5 py-2.5 font-medium text-stone-950 hover:bg-amber-100 disabled:opacity-40">{operation.label}</button>)}
          {point.routes.map(route => <button key={route.id} type="button" disabled={disabled} onClick={() => onInvestigate(route.id)} className="rounded-lg bg-amber-200 px-5 py-2.5 font-medium text-stone-950 hover:bg-amber-100 disabled:opacity-40">{route.label}</button>)}
        </div>
        {point.operations.length + point.routes.length === 0 ? <p className="mt-3 text-sm text-stone-400">{emptyMessage ?? '这次谈话已记入笔记。'}</p> : null}
        {reply?.point === point.id ? <article aria-label="刚取得的信息" className="mt-5 border-l-2 border-amber-200 pl-4"><h4 className="text-sm font-medium text-amber-100">刚取得的信息 · 已存入笔记</h4><p role="status" className="mt-2 whitespace-pre-wrap text-sm leading-7 text-stone-200">{reply.text}</p></article> : null}
      </>}
    </div>
  </section>;
}
