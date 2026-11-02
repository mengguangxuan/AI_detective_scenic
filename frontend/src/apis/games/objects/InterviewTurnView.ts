import { z } from 'zod';
import { witnessIdSchema } from '../../../objects/WitnessId';
import { requestIdSchema } from '../../../objects/RequestId';
import { sequenceSchema } from '../../../objects/Sequence';
import { clueIdSchema } from '../../../objects/ClueId';
const recognitionSchema = z.object({ kind: z.enum(['answer', 'clarification', 'refusal', 'maintaining']), unconfirmed: z.boolean().optional(),
  claims: z.array(z.object({ clue: clueIdSchema, text: z.string().min(1), evidence: z.array(clueIdSchema) }).strict()).max(8) }).strict();
export const interviewTurnSchema = z.object({ requestId: requestIdSchema, sequence: sequenceSchema,
  witness: witnessIdSchema, question: z.string(), shownClues: z.array(clueIdSchema), reply: z.string(),
  mode: z.enum(['answered', 'unknown', 'unavailable', 'rejected']), recognition: recognitionSchema.nullable().optional() }).strict();
export type InterviewTurnView = z.infer<typeof interviewTurnSchema>;
