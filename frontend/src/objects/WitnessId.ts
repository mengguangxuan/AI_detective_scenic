import { z } from 'zod';
export const witnessIdSchema = z.string().regex(/^[a-z][a-z0-9_]{0,63}$/).brand<'WitnessId'>();
export type WitnessId = z.infer<typeof witnessIdSchema>;
