import { z } from 'zod';
import { witnessIdSchema } from '../../../objects/WitnessId';
import { requestIdSchema } from '../../../objects/RequestId';
import { sequenceSchema } from '../../../objects/Sequence';
import { clueIdSchema } from '../../../objects/ClueId';
export const interviewTurnSchema = z.object({ requestId: requestIdSchema, sequence: sequenceSchema,
  witness: witnessIdSchema, question: z.string(), shownClues: z.array(clueIdSchema), reply: z.string(),
  mode: z.enum(['answered', 'unknown', 'unavailable', 'rejected']) }).strict();
export type InterviewTurnView = z.infer<typeof interviewTurnSchema>;
