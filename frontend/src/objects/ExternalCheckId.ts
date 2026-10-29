import { z } from 'zod';
export const externalCheckIdSchema = z.string().regex(/^[a-z][a-z0-9_]{0,63}$/).brand<'ExternalCheckId'>();
export type ExternalCheckId = z.infer<typeof externalCheckIdSchema>;
