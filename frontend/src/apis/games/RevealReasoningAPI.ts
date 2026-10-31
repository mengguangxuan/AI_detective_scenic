import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';
export class RevealReasoningAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number, readonly abandon: boolean) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId,
    expectedSequence: { value: this.expectedSequence }, abandon: this.abandon }, commandResultSchema); }
}
