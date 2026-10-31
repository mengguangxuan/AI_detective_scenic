import { z } from 'zod';
export const personIdSchema = z.string().min(1).max(200).refine(value => value.trim() === value).brand<'PersonId'>();
export type PersonId = z.infer<typeof personIdSchema>;
