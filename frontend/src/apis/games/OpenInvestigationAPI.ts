import type { SessionId } from '../../objects/SessionId';
import { sendMessage } from '../../apiClient/sendMessage';
import { investigationSnapshotSchema } from './objects/InvestigationSnapshot';
export class OpenInvestigationAPI {
  constructor(readonly sessionId: SessionId, readonly caseId: string, readonly revision: number) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, caseId: this.caseId, revision: { value: this.revision } }, investigationSnapshotSchema); }
}
