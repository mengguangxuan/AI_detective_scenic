import { z } from 'zod';
export const operationIdSchema = z.string().min(1).max(200).refine(value => value.trim() === value).brand<'OperationId'>();
export type OperationId = z.infer<typeof operationIdSchema>;
