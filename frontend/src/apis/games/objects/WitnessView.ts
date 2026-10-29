import { z } from 'zod';
import { witnessIdSchema } from '../../../objects/WitnessId';
import { pointIdSchema } from '../../../objects/PointId';
export const witnessViewSchema = z.object({ id: witnessIdSchema, point: pointIdSchema,
  name: z.string().min(1), background: z.string() }).strict();
export type WitnessView = z.infer<typeof witnessViewSchema>;
