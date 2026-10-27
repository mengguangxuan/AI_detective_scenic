import { OpenLocalIdentityAPI } from '../../../apis/accounts/OpenLocalIdentityAPI';
import { OpenStudyAPI } from '../../../apis/games/OpenStudyAPI';
import { GetInvestigationSnapshotAPI } from '../../../apis/games/GetInvestigationSnapshotAPI';
import type { InvestigationSnapshot } from '../../../apis/games/objects/InvestigationSnapshot';
import { sessionIdSchema } from '../../../objects/SessionId';
import type { BrowserSession } from '../objects/BrowserSession';
import { loadBrowserSession, saveBrowserSession } from './browserSessionStorage';
let connecting: Promise<{ stored: BrowserSession; snapshot: InvestigationSnapshot }> | null = null;
export function connectInvestigation() {
  // Deduplicate the StrictMode effect's concurrent initialization, not later state queries.
  if (connecting) return connecting;
  connecting = connect().finally(() => { connecting = null; });
  return connecting;
}
async function connect() {
  let stored = loadBrowserSession();
  if (!stored) {
    stored = { version: 1, sessionId: sessionIdSchema.parse(crypto.randomUUID()), creationPending: true, pending: null };
    saveBrowserSession(stored);
  }
  await new OpenLocalIdentityAPI().send();
  const snapshot = stored.creationPending ? await new OpenStudyAPI(stored.sessionId).send()
    : await new GetInvestigationSnapshotAPI(stored.sessionId).send();
  if (snapshot.sessionId !== stored.sessionId) throw new Error('返回的会话编号不匹配。');
  stored = { ...stored, creationPending: false };
  if (stored.pending?.resultSequence != null) {
    if (snapshot.sequence < stored.pending.resultSequence) throw new Error('当前视图尚未包含已提交结果。');
    stored = { ...stored, pending: null };
  }
  saveBrowserSession(stored);
  return { stored, snapshot };
}
export function newBrowserSession(): void {
  saveBrowserSession({ version: 1, sessionId: sessionIdSchema.parse(crypto.randomUUID()), creationPending: true, pending: null });
}
