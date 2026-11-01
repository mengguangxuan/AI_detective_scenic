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
    <p className="text-sm leading-7 text-stone-300">{ready.snapshot.scene.bundleId === 'woodland' ? '光线从树冠间落下，小径通向林间。沿路走近你想检查的地方，留意地面与周围的细节。' : ready.snapshot.stage?.introduction}</p>
    <TravelPanel choices={ready.snapshot.scene.travel ?? []} disabled={ready.mutation.kind !== 'idle'} onTravel={choice => actions.execute(choice.operation, choice.point)}/>
    {ready.snapshot.police ? <InvestigationLink href="/police" className="inline-block text-sm text-amber-200 underline underline-offset-4">向警方询问医生的初查意见</InvestigationLink> : null}
    <Suspense fallback={<div role="status" className="grid min-h-96 place-items-center rounded-2xl border border-white/10 text-stone-400">正在加载三维场景…</div>}>
      <SceneViewport snapshot={ready.snapshot} selected={ready.selected} disabled={ready.mutation.kind !== 'idle'}
        onInspect={actions.inspect} onExecute={actions.execute} onInvestigate={actions.investigate} cameraMemory={cameraMemory}/>
    </Suspense>
  </section>;
}
