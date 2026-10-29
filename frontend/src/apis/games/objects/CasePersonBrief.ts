import { z } from 'zod';
import { witnessIdSchema } from '../../../objects/WitnessId';
export const casePersonBriefSchema = z.object({ id: z.string().min(1), name: z.string().min(1),
  identity: z.string().min(1), victimRelation: z.string().min(1), caseRelation: z.string().min(1),
  witness: witnessIdSchema.nullable(),
}).strict();
export type CasePersonBrief = z.infer<typeof casePersonBriefSchema>;
