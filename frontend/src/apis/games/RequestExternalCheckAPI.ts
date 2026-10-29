import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import type { ExternalCheckId } from '../../objects/ExternalCheckId';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';
export class RequestExternalCheckAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number, readonly check: ExternalCheckId) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId, expectedSequence: { value: this.expectedSequence }, check: this.check }, commandResultSchema); }
}
