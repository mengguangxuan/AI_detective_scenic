import type { PreviewSession } from '../objects/PreviewSession';
import type { InvestigationAction } from '../objects/InvestigationAction';
import type { PreviewPointId } from '../objects/PreviewPointId';

type Props = { session: PreviewSession; selected: PreviewPointId | null;
  onSelect: (point: PreviewPointId) => void; onExecute: (action: InvestigationAction) => void };
export default function InteractionPanel({ session, selected, onSelect, onExecute }: Props) {
  return <section aria-labelledby="interaction-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <h2 id="interaction-title" className="text-lg font-medium">现场检查</h2>
    <p className="mt-1 text-sm text-stone-400">点击场景中的标记，或在这里选择对象。</p>
    <div className="mt-5 flex gap-2">
      <button type="button" aria-pressed={selected === 'point_drawer'} onClick={() => onSelect('point_drawer')} className="rounded-lg border border-stone-500 px-4 py-2 hover:bg-white/10 aria-pressed:border-amber-300 aria-pressed:text-amber-200">书桌抽屉</button>
      {session.drawerOpened ? <button type="button" aria-pressed={selected === 'point_letter'} onClick={() => onSelect('point_letter')} className="rounded-lg border border-stone-500 px-4 py-2 hover:bg-white/10 aria-pressed:border-amber-300 aria-pressed:text-amber-200">抽屉中的信</button> : null}
    </div>
    <div className="mt-5 min-h-28 border-t border-white/10 pt-4">
      {!selected ? <p className="text-sm text-stone-400">选择一个对象，查看可进行的检查。</p> : selected === 'point_drawer' ? <>
        <p className="text-sm text-stone-300">{session.drawerOpened ? '抽屉已经拉开，里面有一封信。' : '书桌右侧有一个关闭的抽屉。'}</p>
        <button type="button" disabled={session.drawerOpened} onClick={() => onExecute('open-drawer')} className="mt-4 rounded-lg bg-amber-200 px-5 py-2.5 font-medium text-stone-950 hover:bg-amber-100 disabled:cursor-default disabled:bg-white/10 disabled:text-stone-400">{session.drawerOpened ? '已打开' : '打开抽屉'}</button>
      </> : <>
        <p className="text-sm text-stone-300">一封折起的信。检查后将内容记入线索笔记。</p>
        <button type="button" disabled={session.letterRead} onClick={() => onExecute('read-letter')} className="mt-4 rounded-lg bg-amber-200 px-5 py-2.5 font-medium text-stone-950 hover:bg-amber-100 disabled:bg-white/10 disabled:text-stone-400">{session.letterRead ? '已记录' : '阅读信件'}</button>
      </>}
    </div>
  </section>;
}
