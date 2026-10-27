import { z } from 'zod';
import { recordIdSchema } from '../../../objects/RecordId';
import { clueIdSchema } from '../../../objects/ClueId';
import { sequenceSchema } from '../../../objects/Sequence';
export const investigationLogSchema = z.object({ id: recordIdSchema, sequence: sequenceSchema, storyTimeLabel: z.string(), text: z.string(), clueIds: z.array(clueIdSchema) }).strict();
export type InvestigationLogEntry = z.infer<typeof investigationLogSchema>;
