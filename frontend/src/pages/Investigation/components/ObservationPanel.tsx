import type { SceneObservation } from '../../../apis/games/objects/SceneObservation';
import type { SceneNodeId } from '../../../objects/SceneNodeId';
type Props = { observations: readonly SceneObservation[]; selected: SceneNodeId | null; onObserve: (node: SceneNodeId) => void; onOverview: () => void };
export default function ObservationPanel({ observations, selected, onObserve, onOverview }: Props) {
  const item = observations.find(value => value.node === selected);
  return <section aria-labelledby="observation-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <div className="flex items-center justify-between gap-3"><h2 id="observation-title" className="text-lg font-medium">观察环境</h2><button type="button" onClick={onOverview} className="rounded-lg border border-white/25 px-3 py-2 text-sm hover:bg-white/10">恢复全景</button></div>
    <p className="mt-2 text-sm text-stone-400">选择物体切换观察中心。外观说明不会授予案件线索。</p>
    <div className="mt-4 flex flex-wrap gap-2">{observations.map(value => <button key={value.node} type="button" aria-pressed={value.node === selected} onClick={() => onObserve(value.node)} className="rounded-lg border border-stone-500 px-3 py-2 text-sm hover:bg-white/10 aria-pressed:border-amber-200 aria-pressed:text-amber-200">{value.label}</button>)}</div>
    {item ? <article className="mt-5 border-t border-white/10 pt-4"><h3 className="font-medium">正在观察：{item.label}</h3><p className="mt-2 text-sm leading-7 text-stone-300">{item.description}</p></article> : <p className="mt-4 text-sm text-stone-400">也可以点击场景中的物体进行观察。</p>}
  </section>;
}
