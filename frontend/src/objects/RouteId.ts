import { z } from 'zod';
export const routeIdSchema = z.string().min(1).max(200).refine(value => value.trim() === value).brand<'RouteId'>();
export type RouteId = z.infer<typeof routeIdSchema>;
