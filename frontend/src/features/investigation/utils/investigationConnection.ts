import { OpenLocalIdentityAPI } from '../../../apis/accounts/OpenLocalIdentityAPI';
import { OpenStudyAPI } from '../../../apis/games/OpenStudyAPI';
import { OpenInvestigationAPI } from '../../../apis/games/OpenInvestigationAPI';
import { GetInvestigationSnapshotAPI } from '../../../apis/games/GetInvestigationSnapshotAPI';
import type { InvestigationSnapshot } from '../../../apis/games/objects/InvestigationSnapshot';
import { sessionIdSchema } from '../../../objects/SessionId';
import type { BrowserSession } from '../objects/BrowserSession';
import { loadBrowserSession, saveBrowserSession } from './browserSessionStorage';
let connecting: { sessionId: string; promise: Promise<{ stored: BrowserSession; snapshot: InvestigationSnapshot }> } | null = null;
export function connectInvestigation() {
  // Deduplicate the StrictMode effect's concurrent initialization, not later state queries.
  const saved = loadBrowserSession();
  if (!saved) return Promise.reject(new Error('请先从案件区选择案件或恢复调查。'));
  if (connecting?.sessionId === saved.sessionId) return connecting.promise;
  const promise = connect(saved).finally(() => { if (connecting?.promise === promise) connecting = null; });
  connecting = { sessionId: saved.sessionId, promise };
  return promise;
}
async function connect(initial: BrowserSession) {
  let stored = initial;
  await new OpenLocalIdentityAPI().send();
  const snapshot = stored.creationPending ? stored.creation
    ? await new OpenInvestigationAPI(stored.sessionId, stored.creation.caseId, stored.creation.revision).send()
    : await new OpenStudyAPI(stored.sessionId).send()
    : await new GetInvestigationSnapshotAPI(stored.sessionId).send();
  if (snapshot.sessionId !== stored.sessionId) throw new Error('返回的会话编号不匹配。');
  stored = { ...stored, creationPending: false };
  if (stored.pending?.resultSequence != null) {
    if (snapshot.sequence < stored.pending.resultSequence) throw new Error('当前视图尚未包含已提交结果。');
    stored = { ...stored, pending: null };
  }
  if (loadBrowserSession()?.sessionId !== stored.sessionId) throw new Error('已切换调查，请重新连接。');
  saveBrowserSession(stored);
  return { stored, snapshot };
}
export function newBrowserSession(caseId: string, revision: number): void {
  const previous = loadBrowserSession();
  if (previous?.pending || previous?.creationPending) throw new Error('请先返回原调查确认上次请求结果。');
  saveBrowserSession({ version: 1, sessionId: sessionIdSchema.parse(crypto.randomUUID()), creationPending: true,
    creation: { caseId, revision }, pending: null });
}
