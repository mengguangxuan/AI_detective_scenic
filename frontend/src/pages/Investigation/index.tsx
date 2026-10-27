import { lazy, Suspense } from 'react';
import ObservationPanel from './components/ObservationPanel';
import InteractionPanel from './components/InteractionPanel';
import CluePanel from './components/CluePanel';
import InvestigationJournal from './components/InvestigationJournal';
import { useInvestigation } from './hooks/useInvestigation';
const SceneViewport = lazy(() => import('./components/SceneViewport'));

export default function Investigation() {
  const { state, select, observe, overview, execute, investigate, reconnect, retry, reset } = useInvestigation();
  const ready = state.kind === 'ready' ? state : null;
  const disabled = ready?.mutation.kind !== 'idle';
  return <main className="min-h-screen bg-[#101918] px-4 py-7 font-sans text-stone-100 selection:bg-amber-200 selection:text-stone-950 sm:px-8 lg:px-12">
    <div className="mx-auto max-w-[1440px]">
      <header className="flex flex-wrap items-center justify-between gap-5 border-b border-white/10 pb-6">
        <div><p className="text-xs tracking-[0.3em] text-amber-200">AI DETECTIVE THEATRE</p><h1 className="mt-2 text-2xl font-medium tracking-wide sm:text-3xl">AI 侦探剧场</h1></div>
        <div className="flex items-center gap-5"><span className="rounded-full border border-amber-200/25 px-3 py-1.5 text-xs text-amber-200">本机后端演示</span><button type="button" onClick={reset} disabled={state.kind === 'loading' || (ready !== null && disabled)} className="rounded-lg border border-white/20 px-4 py-2 text-sm text-stone-300 hover:bg-white/10 disabled:opacity-40">新建调查</button></div>
      </header>
      <aside className="mb-5 mt-6 rounded-xl border border-amber-200/15 bg-amber-200/5 px-4 py-3 text-sm leading-6 text-stone-300">调查结果由 Scala 后端验证并存入数据库。当前使用本机演示身份，尚未接入正式账户登录。</aside>
      {state.kind === 'loading' ? <p role="status" className="py-12 text-stone-300">正在连接后端并恢复调查…</p> : null}
      {state.kind === 'failed' ? <section role="alert" className="rounded-xl border border-amber-200/20 p-6"><p>{state.message}</p><p className="mt-2 text-sm text-stone-400">请检查后端连接或当前浏览器的演示凭证。不会自动清除已有会话或替换成演示数据。</p><button type="button" onClick={() => void reconnect()} className="mt-4 rounded-lg border border-amber-200 px-4 py-2">重新连接</button></section> : null}
      {ready ? <>
        <div className="mb-7 flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs tracking-widest text-stone-400">最小调查示例 / 现场探索</p><h2 className="mt-2 text-xl">{ready.snapshot.title}</h2></div><p className="text-sm text-stone-400">已提交 <span className="text-amber-200">{ready.snapshot.sequence}</span> 次操作</p></div>
        {ready.mutation.kind === 'outcomeUnknown' ? <section role="alert" className="mb-5 rounded-xl border border-amber-200/30 p-4"><p>{ready.mutation.problem}</p><button type="button" onClick={retry} className="mt-3 rounded-lg border border-amber-200 px-4 py-2">用原请求重试</button></section> : null}
        {ready.mutation.kind === 'awaitingSnapshot' ? <section role="status" className="mb-5 rounded-xl border border-amber-200/30 p-4"><p>{ready.mutation.problem}</p><button type="button" onClick={() => void reconnect()} className="mt-3 rounded-lg border border-amber-200 px-4 py-2">重新读取视图</button></section> : null}
        <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-[minmax(0,1fr)_350px]">
          <div className="min-w-0 space-y-5"><Suspense fallback={<div className="grid min-h-96 place-items-center rounded-2xl border border-white/10 text-stone-400">正在加载三维场景…</div>}><SceneViewport drawerOpened={ready.snapshot.scene.drawerOpened} letterVisible={ready.snapshot.scene.letterVisible} selected={ready.selected.kind === 'point' ? ready.selected.id : null} focus={ready.selected} observations={ready.snapshot.observations} onObserve={observe} onSelect={select}/></Suspense><InteractionPanel points={ready.snapshot.points} selected={ready.selected.kind === 'point' ? ready.selected.id : null} disabled={disabled} onSelect={select} onExecute={execute} onInvestigate={investigate}/></div>
          <div className="min-w-0 space-y-5"><ObservationPanel observations={ready.snapshot.observations} selected={ready.selected.kind === 'observation' ? ready.selected.node : null} onObserve={observe} onOverview={overview}/><CluePanel clues={ready.snapshot.clues}/><InvestigationJournal records={ready.snapshot.journal}/></div>
        </div>
        <p role="status" aria-live="polite" className="mt-6 min-h-6 text-sm text-amber-100">{ready.mutation.kind === 'submitting' ? '正在提交调查…' : ready.notice}</p>
      </> : null}
      <footer className="mt-7 border-t border-white/10 pt-5 text-xs text-stone-500">课程项目 · 后端持久化调查切片 · 正式案件与 AI 审讯尚未接入</footer>
    </div>
  </main>;
}
