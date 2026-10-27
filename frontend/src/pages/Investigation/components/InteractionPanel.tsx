import type { InquiryPointView } from '../../../apis/games/objects/InquiryPointView';
import type { PointId } from '../../../objects/PointId';
import type { OperationId } from '../../../objects/OperationId';
import type { RouteId } from '../../../objects/RouteId';
type Props = { points: readonly InquiryPointView[]; selected: PointId | null; disabled: boolean;
  onSelect: (point: PointId) => void; onExecute: (operation: OperationId) => void; onInvestigate: (route: RouteId) => void };
export default function InteractionPanel({ points, selected, disabled, onSelect, onExecute, onInvestigate }: Props) {
  const point = points.find(p => p.id === selected);
  return <section aria-labelledby="interaction-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <h2 id="interaction-title" className="text-lg font-medium">现场检查</h2>
    <p className="mt-1 text-sm text-stone-400">点击场景中的标记，或在这里选择调查入口。</p>
    <div className="mt-5 flex flex-wrap gap-2">{points.map(p => <button key={p.id} type="button" aria-pressed={selected === p.id} onClick={() => onSelect(p.id)} className="rounded-lg border border-stone-500 px-4 py-2 hover:bg-white/10 aria-pressed:border-amber-300 aria-pressed:text-amber-200">{p.label}</button>)}</div>
    <div className="mt-5 min-h-28 border-t border-white/10 pt-4">
      {!point ? <p className="text-sm text-stone-400">选择一个对象，查看可进行的检查。</p> : <>
        <h3 className="text-sm text-stone-300">{point.label}</h3>
        <div className="mt-4 flex flex-wrap gap-2">
          {point.operations.map(operation => <button key={operation.id} type="button" disabled={disabled} onClick={() => onExecute(operation.id)} className="rounded-lg bg-amber-200 px-5 py-2.5 font-medium text-stone-950 hover:bg-amber-100 disabled:opacity-40">{operation.label}</button>)}
          {point.routes.map(route => <button key={route.id} type="button" disabled={disabled} onClick={() => onInvestigate(route.id)} className="rounded-lg bg-amber-200 px-5 py-2.5 font-medium text-stone-950 hover:bg-amber-100 disabled:opacity-40">{route.label}</button>)}
        </div>
        {point.operations.length + point.routes.length === 0 ? <p className="mt-3 text-sm text-stone-400">当前没有新的检查操作。</p> : null}
      </>}
    </div>
  </section>;
}
