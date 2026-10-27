import { Component, Suspense, lazy, type ReactNode } from 'react';
import { Canvas } from '@react-three/fiber';
import type { PointId } from '../../../../objects/PointId';
const StudyScene = lazy(() => import('./StudyScene'));
class SceneBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() {
    return this.state.failed ? <p role="alert" className="p-8 text-stone-300">3D 场景无法加载。仍可通过“现场检查”的文字入口完成调查。</p> : this.props.children;
  }
}
type Props = { drawerOpened: boolean; letterVisible: boolean; selected: PointId | null; onSelect: (point: PointId) => void };
export default function SceneViewport(props: Props) {
  return <section aria-label="书房三维场景" className="relative h-[420px] min-w-0 overflow-hidden rounded-2xl border border-white/10 bg-[#202d2b] lg:h-[540px]">
    <div className="pointer-events-none absolute left-5 top-5 z-10 rounded-full border border-white/15 bg-stone-950/50 px-3 py-1.5 text-xs text-stone-200">现场 01 / 书房</div>
    <SceneBoundary><Suspense fallback={<p className="p-16 text-stone-300">正在准备书房场景…</p>}><Canvas shadows camera={{ position: [4, 3.5, 5], fov: 43 }} fallback={<p className="p-16">浏览器不支持 WebGL，请使用文字入口调查。</p>}><StudyScene {...props}/></Canvas></Suspense></SceneBoundary>
    <p className="pointer-events-none absolute bottom-4 left-5 rounded-lg bg-stone-950/60 px-3 py-2 text-xs text-stone-200">拖动旋转视角 · 滚轮缩放 · 点击编号选择对象</p>
  </section>;
}
