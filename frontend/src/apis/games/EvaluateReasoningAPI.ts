import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import type { ClueId } from '../../objects/ClueId';
import type { PersonId } from '../../objects/PersonId';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';

export class EvaluateReasoningAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number,
    readonly suspects: readonly PersonId[], readonly evidence: readonly ClueId[], readonly explanation: string) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId,
    expectedSequence: { value: this.expectedSequence }, suspects: this.suspects, evidence: this.evidence, explanation: this.explanation }, commandResultSchema); }
}
