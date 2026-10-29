import { z } from 'zod';
import { witnessIdSchema } from '../../../objects/WitnessId';
import { operationIdSchema } from '../../../objects/OperationId';
import { routeIdSchema } from '../../../objects/RouteId';
export const witnessFollowUpSchema = z.object({
  operation: operationIdSchema, witness: witnessIdSchema, title: z.string(), started: z.boolean(), ready: z.boolean(),
  requirements: z.array(z.object({ label: z.string(), met: z.boolean() }).strict()),
  questions: z.array(z.object({ id: routeIdSchema, label: z.string() }).strict()),
}).strict();
export type WitnessFollowUpView = z.infer<typeof witnessFollowUpSchema>;
