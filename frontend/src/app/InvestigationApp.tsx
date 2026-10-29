import { useEffect, useRef, useState } from 'react';
import { useInvestigation } from '../features/investigation/hooks/useInvestigation';
import type { SessionId } from '../objects/SessionId';
import ScenePage from '../pages/Scene';
import CluesPage from '../pages/Clues';
import InterviewsPage from '../pages/Interviews';
import CasePage from '../pages/Case';
import PeoplePage from '../pages/People';
import JournalPage from '../pages/Journal';
import PolicePage from '../pages/Police';
import type { SceneCameraMemory } from '../pages/Scene/objects/SceneCameraMemory';
import { emptyTimeComparisonDraft, type TimeComparisonDraft } from '../pages/Clues/objects/TimeComparisonDraft';
import StagePanel from './components/StagePanel';
import InvestigationNavigation from './components/InvestigationNavigation';
import { useInvestigationPage } from './hooks/useInvestigationPage';
import { investigationPages } from './objects/InvestigationPage';

export default function InvestigationApp() {
  const investigation = useInvestigation();
  const { state, reconnect, retry, reset } = investigation;
  const page = useInvestigationPage();
  const ready = state.kind === 'ready' ? state : null;
  const disabled = ready?.mutation.kind !== 'idle';
  const cameraMemory = useRef<SceneCameraMemory | null>(null);
  const [clueDraft, setClueDraft] = useState<{ sessionId: SessionId; value: TimeComparisonDraft } | null>(null);
  const content = useRef<HTMLDivElement>(null);
  useEffect(() => {
    document.title = `${investigationPages.find(item => item.path === page)?.label ?? '页面不存在'} · AI侦探剧场`;
    content.current?.focus({ preventScroll: true });
  }, [page]);
  return <main className="min-h-screen bg-[#101918] px-4 py-7 font-sans text-stone-100 selection:bg-amber-200 selection:text-stone-950 sm:px-8 lg:px-12">
    <div className="mx-auto max-w-[1440px]">
      <header className="flex flex-wrap items-center justify-between gap-5 border-b border-white/10 pb-6">
        <div><p className="text-xs tracking-[0.3em] text-amber-200">AI DETECTIVE THEATRE</p><h1 className="mt-2 text-2xl font-medium tracking-wide sm:text-3xl">AI 侦探剧场</h1></div>
        <button type="button" onClick={reset} disabled={state.kind === 'loading' || (ready !== null && disabled)} className="rounded-lg border border-white/20 px-4 py-2 text-sm text-stone-300 hover:bg-white/10 disabled:opacity-40">新建调查</button>
      </header>
      {ready ? <div className="mt-6 flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs tracking-widest text-stone-400">周五上午 / 警方初查</p><h2 className="mt-2 text-xl">{ready.snapshot.title}</h2></div><p className="text-sm text-stone-400">材料 {ready.snapshot.clues.length} 份 · 调查记录 {ready.snapshot.sequence} 条</p></div> : null}
      <InvestigationNavigation page={page}/>
      {page === null ? <section className="rounded-xl border border-white/15 p-6"><h2 className="text-xl">找不到这个页面</h2><p className="mt-3 text-sm text-stone-400">请从上方导航选择要进入的调查页面。</p></section> : <>
        {state.kind === 'loading' ? <p role="status" className="py-12 text-stone-300">正在连接后端并恢复调查…</p> : null}
        {state.kind === 'failed' ? <section role="alert" className="rounded-xl border border-amber-200/20 p-6"><p>{state.message}</p><p className="mt-2 text-sm text-stone-400">请确认后端已启动；若刚更新过项目，请先刷新页面，再重新连接。原存档与待处理请求会保留。</p><button type="button" onClick={() => void reconnect()} className="mt-4 rounded-lg border border-amber-200 px-4 py-2">重新连接</button></section> : null}
        {ready ? <>
          {ready.snapshot.caseId === 'vicarage_study' && ready.snapshot.caseRevision < 5 ? <section role="status" className="mb-5 rounded-xl border border-white/20 p-4 text-sm leading-7 text-stone-300">已恢复原修订的调查，进度与待处理请求保持不变。若要体验新增的雷丁、马普尔问询和案中人物介绍，请用右上角“新建调查”进入当前修订；原调查仍保留在服务器。</section> : null}
          {ready.mutation.kind === 'outcomeUnknown' ? <section role="alert" className="mb-5 rounded-xl border border-amber-200/30 p-4"><p>{ready.mutation.problem}</p><button type="button" onClick={retry} className="mt-3 rounded-lg border border-amber-200 px-4 py-2">用原请求重试</button></section> : null}
          {ready.mutation.kind === 'awaitingSnapshot' ? <section role="status" className="mb-5 rounded-xl border border-amber-200/30 p-4"><p>{ready.mutation.problem}</p><button type="button" onClick={() => void reconnect()} className="mt-3 rounded-lg border border-amber-200 px-4 py-2">重新读取视图</button></section> : null}
          {page !== '/case' ? <StagePanel stage={ready.snapshot.stage} compact/> : null}
          <div ref={content} tabIndex={-1} className="outline-none">
            {page === '/case' ? <CasePage snapshot={ready.snapshot} onSelect={investigation.select}/> : null}
            {page === '/people' ? <PeoplePage snapshot={ready.snapshot} onSelect={investigation.select}/> : null}
            {page === '/journal' ? <JournalPage key={ready.snapshot.sessionId} snapshot={ready.snapshot}/> : null}
            {page === '/police' ? <PolicePage key={ready.snapshot.sessionId} ready={ready} actions={investigation}/> : null}
            {page === '/scene' ? <ScenePage key={ready.snapshot.sessionId} ready={ready} actions={investigation} cameraMemory={cameraMemory}/> : null}
            {page === '/clues' ? <CluesPage ready={ready} onCompare={investigation.compare}
              draft={clueDraft?.sessionId === ready.snapshot.sessionId ? clueDraft.value : emptyTimeComparisonDraft}
              onDraftChange={value => setClueDraft({ sessionId: ready.snapshot.sessionId, value })}/> : null}
            {page === '/interviews' ? <InterviewsPage ready={ready} actions={investigation}/> : null}
          </div>
          <p role="status" aria-live="polite" className="mt-5 min-h-6 text-sm text-amber-100">{ready.mutation.kind === 'submitting' ? '正在提交调查…' : ready.notice}</p>
        </> : null}
      </>}
      <footer className="mt-7 border-t border-white/10 pt-5 text-xs text-stone-500">课程项目 · 《牧师寓所谋杀案》首阶段 · 人物问询 · 调查自动保存</footer>
    </div>
  </main>;
}
