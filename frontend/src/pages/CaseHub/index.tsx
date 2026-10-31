import { useEffect, useSyncExternalStore } from 'react';
import InvestigationLink from '../../app/components/InvestigationLink';
import { browserSessionSnapshot, parseBrowserSessionSnapshot, subscribeBrowserSession } from '../../features/investigation/utils/browserSessionStorage';
import { navigate } from '../../app/hooks/usePathname';
import CaseCards from './components/CaseCards';
import SavedInvestigations from './components/SavedInvestigations';
import { useCaseHub } from './hooks/useCaseHub';
import HomeHero from './components/HomeHero';

export default function CaseHub({ path, needsSelection = false }: { path: string; needsSelection?: boolean }) {
  const hub = useCaseHub();
  const raw = useSyncExternalStore(subscribeBrowserSession, browserSessionSnapshot);
  const parsed = parseBrowserSessionSnapshot(raw);
  const current = parsed?.success ? parsed.data : null;
  const locatorProblem = parsed !== null && !parsed.success;
  const blocked = locatorProblem || !!(current?.pending || current?.creationPending);
  const detailId = path.startsWith('/cases/') ? (() => { try { return decodeURIComponent(path.slice(7)); } catch { return ''; } })() : null;
  const entry = hub.state.kind === 'ready' ? hub.state.cases.find(item => item.caseId === detailId) : null;
  useEffect(() => { document.title = `${path === '/' ? '首页' : detailId !== null ? '案件详情' : '案件区'} · AI侦探剧场`; }, [path, detailId]);
  return <main className="min-h-screen bg-[#101918] px-4 py-8 font-sans text-stone-100 sm:px-8 lg:px-12"><div className="mx-auto max-w-6xl">
    <header className="flex flex-wrap items-center justify-between gap-5 border-b border-white/15 pb-6"><div><p className="text-xs tracking-[0.3em] text-amber-200">AI DETECTIVE THEATRE</p><p className="mt-2 text-xl font-medium">AI 侦探剧场</p></div>
      <nav aria-label="网站导航" className="flex gap-5 text-sm text-stone-300"><InvestigationLink href="/" aria-current={path === '/' ? 'page' : undefined}>首页</InvestigationLink><InvestigationLink href="/cases" aria-current={path === '/cases' ? 'page' : undefined}>案件区</InvestigationLink><a href="#my-investigations">我的调查</a></nav></header>
    {path === '/' ? <HomeHero canContinue={current !== null}/>
      : <h1 className="my-8 text-3xl font-medium">{detailId !== null ? '案件详情' : '案件区'}</h1>}
    {needsSelection ? <p role="status" className="mb-6 text-sm text-amber-100">请先选择案件，或从“我的调查”恢复一份进度。</p> : null}
    {locatorProblem ? <p role="alert" className="mb-6 rounded-xl border border-amber-200/30 p-5 text-sm leading-7 text-amber-100">无法读取本机调查定位。原数据仍保留，请恢复浏览器存储访问后刷新；当前不会覆盖它或新建调查。</p> : null}
    {current && blocked ? <section role="status" className="mb-6 rounded-xl border border-amber-200/30 p-5 text-sm leading-7 text-amber-100">上次开局或调查请求尚未确认，请先返回原调查完成恢复。<button type="button" onClick={() => navigate('/case')} className="ml-3 underline underline-offset-4">返回待确认调查</button></section> : null}
    {hub.state.kind === 'loading' ? <p role="status" className="py-10 text-stone-400">正在读取案件与调查记录…</p> : null}
    {hub.state.kind === 'failed' ? <section role="alert" className="my-6 rounded-xl border border-amber-200/30 p-5"><p>{hub.state.message}</p><button type="button" onClick={hub.reload} className="mt-4 rounded-lg border border-amber-200 px-4 py-2">重新读取案件区</button></section> : null}
    {hub.state.kind === 'ready' ? <div className="space-y-10">
      {detailId === null ? <CaseCards cases={hub.state.cases}/> : entry ? <section aria-label="案件介绍" className="rounded-2xl border border-white/15 bg-[#182421] p-6"><p className="text-xs text-amber-200">{entry.tags.join(' · ')}</p><h2 className="mt-3 text-2xl font-medium">{entry.title}</h2><p className="mt-5 max-w-3xl text-sm leading-8 text-stone-300">{entry.summary}</p><button type="button" disabled={hub.busy || blocked} onClick={() => hub.start(entry)} className="mt-6 rounded-lg bg-amber-200 px-6 py-3 font-medium text-stone-950 disabled:opacity-40">开始新调查</button><p className="mt-3 text-xs leading-6 text-stone-400">将创建一份独立进度，已有调查保留。未提交的文字请先提交或留存。</p></section> : <section><p>该案件暂未开放。</p><InvestigationLink href="/cases" className="mt-3 inline-block text-amber-200 underline">返回案件区</InvestigationLink></section>}
      <SavedInvestigations investigations={detailId === null ? hub.state.investigations : hub.state.investigations.filter(item => item.caseId === detailId)} currentId={current?.sessionId ?? null} blocked={blocked} busy={hub.busy} onResume={id => void hub.resume(id)}/>
      <section aria-labelledby="author-title" className="rounded-xl border border-dashed border-white/20 p-6"><h2 id="author-title" className="font-medium">带来你自己的案件</h2><p className="mt-3 text-sm leading-7 text-stone-400">未来这里将支持上传案件、校验内容并私有试玩。上传功能尚未开放。</p></section>
    </div> : null}
    {hub.notice ? <p role="alert" className="mt-6 text-sm text-amber-100">{hub.notice}</p> : null}
    <footer className="mt-10 border-t border-white/10 pt-5 text-xs leading-6 text-stone-500">课程项目 · 从现场发现线索，用推理连接事实</footer>
  </div></main>;
}
