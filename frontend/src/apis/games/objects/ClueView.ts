import { z } from 'zod';
import { clueIdSchema } from '../../../objects/ClueId';
export const clueViewSchema = z.object({ id: clueIdSchema, title: z.string(), text: z.string() }).strict();
export type ClueView = z.infer<typeof clueViewSchema>;
