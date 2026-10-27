import { z } from 'zod';
export const sessionIdSchema = z.string().min(1).max(200).refine(value => value.trim() === value).brand<'SessionId'>();
export type SessionId = z.infer<typeof sessionIdSchema>;
