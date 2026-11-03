import { lazy, Suspense, type RefObject } from 'react';
import type { InvestigationPageState } from '../../features/investigation/objects/InvestigationPageState';
import type { useInvestigation } from '../../features/investigation/hooks/useInvestigation';
import type { SceneCameraMemory } from './objects/SceneCameraMemory';
import InvestigationLink from '../../app/components/InvestigationLink';
import TravelPanel from '../../features/investigation/components/TravelPanel';
const SceneViewport = lazy(() => import('./components/SceneViewport'));
type Props = { ready: Extract<InvestigationPageState, { kind: 'ready' }>;
  actions: Pick<ReturnType<typeof useInvestigation>, 'inspect' | 'execute' | 'investigate'>;
  cameraMemory: RefObject<SceneCameraMemory | null> };
export default function ScenePage({ ready, actions, cameraMemory }: Props) {
  return <section aria-labelledby="scene-page-title" className="space-y-4">
    <h2 id="scene-page-title" className="text-xl font-medium">现场探索</h2>
    <p className="text-sm leading-7 text-stone-300">{ready.snapshot.scene.render?.room.description ?? `当前位置：${ready.snapshot.scene.placeName}。选择可见物品查看详情，沿可用路线前往其他地点。`}</p>
    {ready.snapshot.scene.render ? <p className="text-xs leading-6 text-stone-400">画面为现场布局示意，未绘制的物品不代表不存在。检查结果请以实际取得的记录为准。</p> : null}
    <TravelPanel choices={ready.snapshot.scene.travel ?? []} disabled={ready.mutation.kind !== 'idle'} onTravel={choice => actions.execute(choice.operation, choice.point)}/>
    {ready.snapshot.police ? <InvestigationLink href="/police" className="inline-block text-sm text-amber-200 underline underline-offset-4">与{ready.snapshot.police.name}交流</InvestigationLink> : null}
    <Suspense fallback={<div role="status" className="grid min-h-96 place-items-center rounded-2xl border border-white/10 text-stone-400">正在加载三维场景…</div>}>
      <SceneViewport snapshot={ready.snapshot} selected={ready.selected} disabled={ready.mutation.kind !== 'idle'}
        onInspect={actions.inspect} onExecute={actions.execute} onInvestigate={actions.investigate} cameraMemory={cameraMemory}/>
    </Suspense>
  </section>;
}
