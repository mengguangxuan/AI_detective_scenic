import { z } from 'zod';
import { sequenceSchema } from '../../../objects/Sequence';
import { requestIdSchema } from '../../../objects/RequestId';
import { personIdSchema } from '../../../objects/PersonId';
import { clueIdSchema } from '../../../objects/ClueId';
const checkSchema = z.object({ id: z.string(), label: z.string(), satisfied: z.boolean() }).strict();
export const reasoningProgressSchema = z.object({ attempts: z.array(z.object({
  sequence: sequenceSchema, requestId: requestIdSchema, suspects: z.array(personIdSchema).min(1), evidence: z.array(clueIdSchema).min(1).max(50),
  explanation: z.string().min(10).max(1000), status: z.enum(['Revise', 'Established', 'Unavailable', 'Rejected']),
  checks: z.array(checkSchema), feedback: z.string().min(1),
}).strict()), ending: z.object({ sequence: sequenceSchema, mode: z.enum(['Solved', 'Revealed']), review: z.string().min(1) }).strict().nullable() }).strict();
export type ReasoningProgress = z.infer<typeof reasoningProgressSchema>;
