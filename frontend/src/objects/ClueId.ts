import { z } from 'zod';
export const clueIdSchema = z.string().min(1).max(200).refine(value => value.trim() === value).brand<'ClueId'>();
export type ClueId = z.infer<typeof clueIdSchema>;
