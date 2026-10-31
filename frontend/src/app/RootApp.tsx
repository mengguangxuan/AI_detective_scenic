import { useEffect, useState, useSyncExternalStore } from 'react';
import InvestigationApp from './InvestigationApp';
import { usePathname } from './hooks/usePathname';
import { investigationPages } from './objects/InvestigationPage';
import { browserSessionSnapshot, parseBrowserSessionSnapshot, subscribeBrowserSession } from '../features/investigation/utils/browserSessionStorage';
import CaseHub from '../pages/CaseHub';

export default function RootApp() {
  const path = usePathname().replace(/\/+$/, '') || '/';
  const raw = useSyncExternalStore(subscribeBrowserSession, browserSessionSnapshot);
  const locator = parseBrowserSessionSnapshot(raw);
  const sessionId = locator?.success ? locator.data.sessionId : null;
  const isInvestigation = investigationPages.some(page => page.path === path);
  const [visited, setVisited] = useState(isInvestigation && sessionId !== null);
  useEffect(() => { if (isInvestigation && sessionId) setVisited(true); }, [isInvestigation, sessionId]);
  const hubPath = path === '/' || path === '/cases' || path.startsWith('/cases/');
  const showWorkspace = isInvestigation || (!hubPath && sessionId !== null);
  return <>
    {hubPath || (isInvestigation && !sessionId) ? <CaseHub path={isInvestigation ? '/cases' : path} needsSelection={isInvestigation}/> : null}
    {(visited || showWorkspace) && sessionId ? <div hidden={!showWorkspace}><InvestigationApp key={sessionId}/></div> : null}
    {!hubPath && !isInvestigation && !sessionId ? <main className="min-h-screen bg-[#101918] p-8 text-stone-100"><h1 className="text-2xl">找不到这个页面</h1><a className="mt-5 inline-block text-amber-200 underline" href="/cases">返回案件区</a></main> : null}
  </>;
}
