import { z } from 'zod';
import { witnessIdSchema } from '../../../objects/WitnessId';
import { sessionIdSchema } from '../../../objects/SessionId';
import { requestIdSchema } from '../../../objects/RequestId';
import { operationIdSchema } from '../../../objects/OperationId';
import { pointIdSchema } from '../../../objects/PointId';
import { routeIdSchema } from '../../../objects/RouteId';
import { clueIdSchema } from '../../../objects/ClueId';
import { timeAssessmentSchema } from '../../../apis/games/objects/TimeAssessment';
const meta = { sessionId: sessionIdSchema, requestId: requestIdSchema, expectedSequence: z.number().int().nonnegative().safe() };
export const pendingCommandSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('execute'), ...meta, operation: operationIdSchema }).strict(),
  z.object({ kind: z.literal('investigate'), ...meta, point: pointIdSchema, route: routeIdSchema }).strict(),
  z.object({ kind: z.literal('report'), ...meta, evidence: z.array(clueIdSchema).min(1).max(20) }).strict(),
  z.object({ kind: z.literal('ask'), ...meta, witness: witnessIdSchema, question: z.string().min(1).max(1000), evidence: z.array(clueIdSchema).max(20) }).strict(),
  z.object({ kind: z.literal('compare'), ...meta, evidence: z.array(clueIdSchema),
    convertedTimeMinutes: z.number().int().min(0).max(1439), assessment: timeAssessmentSchema }).strict(),
]);
export type PendingCommand = z.infer<typeof pendingCommandSchema>;
