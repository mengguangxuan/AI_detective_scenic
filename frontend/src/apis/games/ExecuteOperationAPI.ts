import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import type { OperationId } from '../../objects/OperationId';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';
export class ExecuteOperationAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number, readonly operation: OperationId) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId, expectedSequence: { value: this.expectedSequence }, operation: this.operation }, commandResultSchema); }
}
