import { useEffect, useState } from 'react';
import { GetCaseCatalogAPI } from '../../../apis/cases/GetCaseCatalogAPI';
import type { CaseCatalogEntry } from '../../../apis/cases/objects/CaseCatalogEntry';
import { OpenLocalIdentityAPI } from '../../../apis/accounts/OpenLocalIdentityAPI';
import { ListInvestigationsAPI } from '../../../apis/games/ListInvestigationsAPI';
import { GetInvestigationSnapshotAPI } from '../../../apis/games/GetInvestigationSnapshotAPI';
import type { SavedInvestigationView } from '../../../apis/games/objects/SavedInvestigationView';
import type { SessionId } from '../../../objects/SessionId';
import { loadBrowserSession, saveBrowserSession } from '../../../features/investigation/utils/browserSessionStorage';
import { newBrowserSession } from '../../../features/investigation/utils/investigationConnection';
import { navigate } from '../../../app/hooks/usePathname';

type HubState = { kind: 'loading' } | { kind: 'failed'; message: string } |
  { kind: 'ready'; cases: readonly CaseCatalogEntry[]; investigations: readonly SavedInvestigationView[] };
let loading: Promise<{ cases: CaseCatalogEntry[]; investigations: SavedInvestigationView[] }> | null = null;
function readHub() {
  if (!loading) loading = Promise.all([
    new GetCaseCatalogAPI().send(),
    new OpenLocalIdentityAPI().send().then(() => new ListInvestigationsAPI().send()),
  ]).then(([cases, investigations]) => ({ cases, investigations })).finally(() => { loading = null; });
  return loading;
}
function message(error: unknown) { return error instanceof Error ? error.message : '暂时无法连接后端，请重试。'; }
export function useCaseHub() {
  const [state, setState] = useState<HubState>({ kind: 'loading' });
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  useEffect(() => {
    let active = true;
    setState({ kind: 'loading' });
    void readHub().then(result => { if (active) setState({ kind: 'ready', ...result }); })
      .catch(error => { if (active) setState({ kind: 'failed', message: message(error) }); });
    return () => { active = false; };
  }, [refresh]);
  function start(entry: CaseCatalogEntry) {
    if (busy) return;
    try { newBrowserSession(entry.caseId, entry.revision); navigate('/case'); }
    catch (error) { setNotice(message(error)); }
  }
  async function resume(id: SessionId) {
    if (busy) return;
    try {
      const current = loadBrowserSession();
      if (current?.sessionId === id) { navigate('/case'); return; }
      if (current?.pending || current?.creationPending) throw new Error('请先返回待确认的调查，完成原请求后再切换。');
      setBusy(true);
      const snapshot = await new GetInvestigationSnapshotAPI(id).send();
      const latest = loadBrowserSession();
      if (latest?.pending || latest?.creationPending || latest?.sessionId !== current?.sessionId) throw new Error('当前调查已有变化，请确认原请求后再切换。');
      if (snapshot.sessionId !== id) throw new Error('返回的调查编号不一致，未切换调查。');
      saveBrowserSession({ version: 1, sessionId: id, creationPending: false, pending: null });
      navigate('/case');
    } catch (error) { setNotice(message(error)); }
    finally { setBusy(false); }
  }
  return { state, busy, notice, start, resume, reload: () => setRefresh(value => value + 1) };
}
