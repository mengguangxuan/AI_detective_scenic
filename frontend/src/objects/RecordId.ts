import { z } from 'zod';
export const recordIdSchema = z.string().min(1).max(220).refine(value => value.trim() === value).brand<'RecordId'>();
export type RecordId = z.infer<typeof recordIdSchema>;
