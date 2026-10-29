import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import type { ClueId } from '../../objects/ClueId';
import type { TimeAssessment } from './objects/TimeAssessment';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';

export class CompareTimesAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number,
    readonly evidence: readonly ClueId[], readonly convertedTimeMinutes: number, readonly assessment: TimeAssessment) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId,
    expectedSequence: { value: this.expectedSequence }, evidence: this.evidence,
    convertedTimeMinutes: this.convertedTimeMinutes, assessment: this.assessment }, commandResultSchema); }
}
