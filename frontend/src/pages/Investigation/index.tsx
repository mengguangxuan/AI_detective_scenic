import { lazy, Suspense } from 'react';
const SceneViewport = lazy(() => import('./components/SceneViewport'));
import InteractionPanel from './components/InteractionPanel';
import CluePanel from './components/CluePanel';
import InvestigationJournal from './components/InvestigationJournal';
import { useInvestigationPreview } from './hooks/useInvestigationPreview';

export default function Investigation() {
  const { state, select, execute, reset } = useInvestigationPreview();
  return <main className="min-h-screen bg-[#101918] px-4 py-7 font-sans text-stone-100 selection:bg-amber-200 selection:text-stone-950 sm:px-8 lg:px-12">
    <div className="mx-auto max-w-[1440px]">
      <header className="flex flex-wrap items-center justify-between gap-5 border-b border-white/10 pb-6">
        <div><p className="text-xs tracking-[0.3em] text-amber-200">AI DETECTIVE THEATRE</p><h1 className="mt-2 text-2xl font-medium tracking-wide sm:text-3xl">AI 侦探剧场</h1></div>
        <div className="flex items-center gap-5"><span className="rounded-full border border-amber-200/25 px-3 py-1.5 text-xs text-amber-200">本地预览</span><button type="button" onClick={reset} className="rounded-lg border border-white/20 px-4 py-2 text-sm text-stone-300 hover:bg-white/10">重新开始预览</button></div>
      </header>
      <div className="mb-7 mt-6 flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs tracking-widest text-stone-400">最小调查示例 / 现场探索</p><h2 className="mt-2 text-xl">书房里的一封信</h2></div><p className="text-sm text-stone-400">已完成 <span className="text-amber-200">{state.session.sequence}</span> / 2 步调查</p></div>
      <aside className="mb-5 rounded-xl border border-amber-200/15 bg-amber-200/5 px-4 py-3 text-sm leading-6 text-stone-300">本地交互预览，尚未连接 Scala 后端。进度仅保存在此浏览器；预览线索属于公开示例数据。</aside>
      {state.storageProblem ? <p role="alert" className="mb-5 text-sm text-amber-200">{state.storageProblem}</p> : null}
      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-[minmax(0,1fr)_350px]">
        <div className="min-w-0 space-y-5"><Suspense fallback={<div className="grid min-h-96 place-items-center rounded-2xl border border-white/10 text-stone-400">正在加载三维场景…</div>}><SceneViewport drawerOpened={state.session.drawerOpened} selected={state.selected} onSelect={select}/></Suspense><InteractionPanel session={state.session} selected={state.selected} onSelect={select} onExecute={execute}/></div>
        <div className="min-w-0 space-y-5"><CluePanel letterRead={state.session.letterRead}/><InvestigationJournal session={state.session}/><section className="px-1 text-xs leading-6 text-stone-500"><h2 className="font-medium text-stone-400">调查提示</h2><p>先检查抽屉，再检查出现的信件。画面只负责呈现场景，线索记录与操作分开显示。</p></section></div>
      </div>
      <p role="status" aria-live="polite" className="mt-6 min-h-6 text-sm text-amber-100">{state.notice}</p>
      <footer className="mt-7 border-t border-white/10 pt-5 text-xs text-stone-500">课程项目 · 调查页首版预览 · 正式案件与 AI 审讯尚未接入</footer>
    </div>
  </main>;
}
