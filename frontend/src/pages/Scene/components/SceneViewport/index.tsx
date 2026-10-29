import { Component, Suspense, lazy, useEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import { Canvas, type ThreeEvent } from '@react-three/fiber';
import type { InvestigationSnapshot } from '../../../../apis/games/objects/InvestigationSnapshot';
import type { SceneObjectBinding } from '../../../../apis/games/objects/SceneObjectBinding';
import type { SceneSelection } from '../../../../features/investigation/objects/SceneSelection';
import type { SceneNodeId } from '../../../../objects/SceneNodeId';
import type { OperationId } from '../../../../objects/OperationId';
import type { RouteId } from '../../../../objects/RouteId';
import type { SceneCameraMemory } from '../../objects/SceneCameraMemory';
import ObjectMenu from './ObjectMenu';
const StudyScene = lazy(() => import('./StudyScene'));
class SceneBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() {
    return this.state.failed ? <p role="alert" className="p-8 text-stone-300">3D 场景无法加载，请使用下方的物品列表调查。</p> : this.props.children;
  }
}
type Props = { cameraMemory: RefObject<SceneCameraMemory | null>; snapshot: InvestigationSnapshot; selected: SceneSelection; disabled: boolean;
  onInspect: (object: SceneObjectBinding) => void; onExecute: (operation: OperationId) => void; onInvestigate: (route: RouteId) => void };
export default function SceneViewport({ snapshot, selected, disabled, onInspect, onExecute, onInvestigate, cameraMemory }: Props) {
  const frame = useRef<HTMLDivElement>(null);
  const gesture = useRef({ x: 0, y: 0, dragged: false });
  const [menu, setMenu] = useState<{ node: SceneNodeId; x: number; y: number } | null>(null);
  const [resetVersion, setResetVersion] = useState(0);
  const [size, setSize] = useState({ width: 320, height: 460 });
  useEffect(() => {
    const element = frame.current;
    if (!element) return;
    const resize = new ResizeObserver(() => setSize({ width: element.clientWidth, height: element.clientHeight }));
    resize.observe(element);
    return () => resize.disconnect();
  }, []);
  useEffect(() => {
    function close(event: KeyboardEvent) { if (event.key === 'Escape') setMenu(null); }
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, []);
  const object = snapshot.scene.objects.find(o => o.node === menu?.node);
  const isSelected = object && (object.point ? selected.kind === 'point' && selected.id === object.point
    : selected.kind === 'observation' && selected.node === object.node);
  function pick(object: SceneObjectBinding, x: number, y: number) {
    setMenu({ node: object.node, x, y });
    onInspect(object);
  }
  function pickModel(object: SceneObjectBinding, event: ThreeEvent<MouseEvent>) {
    if (gesture.current.dragged || event.delta > 5) return;
    const rect = frame.current?.getBoundingClientRect();
    if (rect) pick(object, Math.max(12, Math.min(event.clientX - rect.left + 12, rect.width - 332)),
      Math.max(52, Math.min(event.clientY - rect.top, rect.height - 310)));
  }
  return <section aria-label="现场探索" className="min-w-0 overflow-hidden rounded-2xl border border-white/10 bg-[#202d2b]">
    <div ref={frame} aria-label="书房三维场景" className="relative h-[460px] min-w-0 lg:h-[540px]"
      onPointerDownCapture={event => { gesture.current = { x: event.clientX, y: event.clientY, dragged: false }; }}
      onPointerMoveCapture={event => { if (Math.hypot(event.clientX - gesture.current.x, event.clientY - gesture.current.y) > 5) gesture.current.dragged = true; }}>
      <div className="pointer-events-none absolute left-4 top-4 z-10 rounded-full bg-stone-950/60 px-3 py-2 text-xs text-stone-200">牧师寓所 / 书房</div>
      <button type="button" onClick={() => { setResetVersion(v => v + 1); setMenu(null); }} className="absolute right-4 top-4 z-10 rounded-lg border border-white/25 bg-stone-950/70 px-3 py-2 text-xs text-stone-100 hover:bg-stone-800">恢复全景</button>
      <SceneBoundary><Suspense fallback={<p className="p-16 text-stone-300">正在准备书房…</p>}>
        <Canvas shadows camera={{ position: [4, 3.5, 5], fov: 43 }} onPointerMissed={() => { if (!gesture.current.dragged) setMenu(null); }}
          fallback={<p className="p-16">浏览器不支持 WebGL，请使用下方物品列表。</p>}>
          <StudyScene scene={snapshot.scene} sessionId={snapshot.sessionId} cameraMemory={cameraMemory} resetVersion={resetVersion} onPick={pickModel}/>
        </Canvas>
      </Suspense></SceneBoundary>
      {menu && object && isSelected ? <ObjectMenu object={object} point={snapshot.points.find(p => p.id === object.point)}
        observation={snapshot.observations.find(o => o.node === object.node)}
        left={Math.max(12, Math.min(menu.x, size.width - 332))} top={Math.max(52, Math.min(menu.y, size.height - 180))}
        disabled={disabled} onClose={() => setMenu(null)} onExecute={onExecute} onInvestigate={onInvestigate}/> : null}
      <p className="pointer-events-none absolute bottom-3 left-3 right-3 rounded-lg bg-stone-950/70 px-3 py-2 text-xs leading-5 text-stone-200">左键拖动平移 · 右键拖动转向 · 滚轮缩放 · 点击物品操作</p>
    </div>
    <details className="border-t border-white/10 px-4 py-3 text-sm text-stone-300">
      <summary className="cursor-pointer">物品列表（备用入口）</summary>
      <div className="mt-3 flex flex-wrap gap-2">{snapshot.scene.objects.map(object => <button key={object.node} type="button" onClick={() => pick(object, 16, 70)} className="rounded-lg border border-white/25 px-3 py-2 hover:bg-white/10">{object.label}</button>)}</div>
    </details>
  </section>;
}
