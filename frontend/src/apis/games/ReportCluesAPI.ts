import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import type { ClueId } from '../../objects/ClueId';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';

export class ReportCluesAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number,
    readonly evidence: readonly ClueId[]) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId,
    expectedSequence: { value: this.expectedSequence }, evidence: this.evidence }, commandResultSchema); }
}
