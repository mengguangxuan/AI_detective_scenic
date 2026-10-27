import { z } from 'zod';
import { sequenceSchema } from '../../../objects/Sequence';
import { clueIdSchema } from '../../../objects/ClueId';
export const commandResultSchema = z.object({ sequence: sequenceSchema, findings: z.array(z.string()), acquiredClues: z.array(clueIdSchema) }).strict();
export type CommandResult = z.infer<typeof commandResultSchema>;
