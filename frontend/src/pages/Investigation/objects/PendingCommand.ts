import { z } from 'zod';
import { sessionIdSchema } from '../../../objects/SessionId';
import { requestIdSchema } from '../../../objects/RequestId';
import { operationIdSchema } from '../../../objects/OperationId';
import { pointIdSchema } from '../../../objects/PointId';
import { routeIdSchema } from '../../../objects/RouteId';
const meta = { sessionId: sessionIdSchema, requestId: requestIdSchema, expectedSequence: z.number().int().nonnegative().safe() };
export const pendingCommandSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('execute'), ...meta, operation: operationIdSchema }).strict(),
  z.object({ kind: z.literal('investigate'), ...meta, point: pointIdSchema, route: routeIdSchema }).strict(),
]);
export type PendingCommand = z.infer<typeof pendingCommandSchema>;
