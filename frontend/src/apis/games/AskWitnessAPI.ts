import type { WitnessId } from '../../objects/WitnessId';
import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import type { ClueId } from '../../objects/ClueId';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';
export class AskWitnessAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number,
    readonly witness: WitnessId, readonly question: string, readonly evidence: readonly ClueId[]) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId,
    expectedSequence: { value: this.expectedSequence }, witness: this.witness, question: this.question, evidence: this.evidence }, commandResultSchema, 75000); }
}
