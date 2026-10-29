import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';
export class AdvanceTimeAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number, readonly minutes: number) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId, expectedSequence: { value: this.expectedSequence }, minutes: this.minutes }, commandResultSchema); }
}
