import { z } from 'zod';
import { externalCheckIdSchema } from '../../../objects/ExternalCheckId';
export const externalCheckViewSchema = z.object({
  id: externalCheckIdSchema, title: z.string(), requestLabel: z.string(), delayMinutes: z.number().int().positive(), available: z.boolean(),
  progress: z.object({ requestedAt: z.string(), readyAt: z.string(), completedAt: z.string().nullable() }).strict().nullable(),
}).strict();
