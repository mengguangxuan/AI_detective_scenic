import { z } from 'zod';
export const pointIdSchema = z.string().min(1).max(200).refine(value => value.trim() === value).brand<'PointId'>();
export type PointId = z.infer<typeof pointIdSchema>;
