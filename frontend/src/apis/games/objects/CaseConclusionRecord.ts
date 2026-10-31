import { z } from 'zod';
import { personIdSchema } from '../../../objects/PersonId';
import { clueIdSchema } from '../../../objects/ClueId';
import { sequenceSchema } from '../../../objects/Sequence';
export const caseConclusionSchema = z.object({ sequence: sequenceSchema,
  suspects: z.array(personIdSchema).min(1), evidence: z.array(clueIdSchema).min(1).max(50), explanation: z.string().min(10).max(1000),
  suspectScore: z.union([z.literal(0), z.literal(25), z.literal(50)]), evidenceScore: z.number().int().min(0).max(50),
  coverage: z.array(z.object({ label: z.string(), requiredClues: z.array(clueIdSchema).min(1), covered: z.boolean() }).strict()).length(5),
  review: z.string().min(1),
}).strict();
export type CaseConclusionRecord = z.infer<typeof caseConclusionSchema>;
