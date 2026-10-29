import { useEffect, useRef } from 'react';
import type { SceneObjectBinding } from '../../../../apis/games/objects/SceneObjectBinding';
import type { InquiryPointView } from '../../../../apis/games/objects/InquiryPointView';
import type { SceneObservation } from '../../../../apis/games/objects/SceneObservation';
import type { OperationId } from '../../../../objects/OperationId';
import type { RouteId } from '../../../../objects/RouteId';

type Props = { object: SceneObjectBinding; point: InquiryPointView | undefined; observation: SceneObservation | undefined;
  left: number; top: number; disabled: boolean; onClose: () => void;
  onExecute: (operation: OperationId) => void; onInvestigate: (route: RouteId) => void };
export default function ObjectMenu({ object, point, observation, left, top, disabled, onClose, onExecute, onInvestigate }: Props) {
  const menu = useRef<HTMLElement>(null);
  // Focus only when the item changes, so a saved operation can update its menu without stealing focus again.
  useEffect(() => { menu.current?.focus({ preventScroll: true }); }, [object.node]);
  return <aside ref={menu} role="dialog" aria-label={`${object.label}操作`} tabIndex={-1}
    onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); onClose(); } }}
    style={{ left, top, maxHeight: `calc(100% - ${top + 56}px)` }} className="absolute z-20 w-80 max-w-[calc(100%_-_24px)] overflow-y-auto rounded-xl border border-amber-200/40 bg-[#17231f]/95 p-4 text-sm shadow-2xl outline-none">
    <div className="flex items-center justify-between gap-3"><h3 className="font-medium text-amber-100">{object.label}</h3><button type="button" aria-label="关闭物品菜单" onClick={onClose} className="rounded px-2 py-1 text-stone-300 hover:bg-white/10">关闭</button></div>
    {observation ? <p className="mt-2 leading-6 text-stone-300">{observation.description}</p> : null}
    {point ? <div className="mt-3 flex flex-wrap gap-2">
      {point.operations.map(action => <button key={action.id} type="button" disabled={disabled} onClick={() => onExecute(action.id)} className="rounded-lg bg-amber-200 px-4 py-2 text-stone-950 disabled:opacity-40">{action.label}</button>)}
      {point.routes.map(action => <button key={action.id} type="button" disabled={disabled} onClick={() => onInvestigate(action.id)} className="rounded-lg border border-amber-200/50 px-3 py-2 text-amber-100 disabled:opacity-40">{action.label}</button>)}
    </div> : null}
    {disabled ? <p role="status" className="mt-3 text-xs text-amber-100">正在处理调查，请稍候…</p> : null}
    {point?.findings.map(finding => <article key={finding.id} className="mt-4 border-t border-white/10 pt-3"><h4 className="text-xs text-stone-400">{finding.title}</h4><p className="mt-2 whitespace-pre-wrap leading-7 text-stone-100">{finding.text}</p></article>)}
  </aside>;
}
