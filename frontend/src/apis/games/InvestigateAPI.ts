import type { SessionId } from '../../objects/SessionId';
import type { RequestId } from '../../objects/RequestId';
import type { PointId } from '../../objects/PointId';
import type { RouteId } from '../../objects/RouteId';
import { sendMessage } from '../../apiClient/sendMessage';
import { commandResultSchema } from './objects/CommandResult';
export class InvestigateAPI {
  constructor(readonly sessionId: SessionId, readonly requestId: RequestId, readonly expectedSequence: number, readonly point: PointId, readonly route: RouteId) {}
  send() { return sendMessage(this.constructor.name, { sessionId: this.sessionId, requestId: this.requestId, expectedSequence: { value: this.expectedSequence }, point: this.point, route: this.route }, commandResultSchema); }
}
