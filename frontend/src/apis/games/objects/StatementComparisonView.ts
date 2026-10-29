import { z } from 'zod';
import { witnessIdSchema } from '../../../objects/WitnessId';
import { sequenceSchema } from '../../../objects/Sequence';
import { clueViewSchema } from './ClueView';
const versionSchema = z.object({ clue: clueViewSchema, sequence: sequenceSchema, recordedAt: z.string() }).strict();
export const statementComparisonSchema = z.object({
  witness: witnessIdSchema, title: z.string(), nextObjective: z.string().nullable(),
  rows: z.array(z.object({ label: z.string(), initial: versionSchema.nullable(), revised: versionSchema.nullable() }).strict()),
}).strict();
export type StatementComparisonView = z.infer<typeof statementComparisonSchema>;
