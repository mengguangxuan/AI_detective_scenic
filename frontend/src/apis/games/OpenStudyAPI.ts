import type { SessionId } from '../../objects/SessionId';
import { sendMessage } from '../../apiClient/sendMessage';
import { investigationSnapshotSchema } from './objects/InvestigationSnapshot';
export class OpenStudyAPI {
  constructor(readonly sessionId: SessionId) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId }, investigationSnapshotSchema); }
}
