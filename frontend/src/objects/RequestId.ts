import { z } from 'zod';
export const requestIdSchema = z.string().min(1).max(200).refine(value => value.trim() === value).brand<'RequestId'>();
export type RequestId = z.infer<typeof requestIdSchema>;
