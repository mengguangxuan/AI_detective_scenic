import { z } from 'zod';
import { operationIdSchema } from '../../../objects/OperationId';
import { pointIdSchema } from '../../../objects/PointId';
export const travelChoiceSchema = z.object({ operation: operationIdSchema, point: pointIdSchema,
  label: z.string().min(1), destination: z.string().min(1), destinationName: z.string().min(1) }).strict();
export type TravelChoiceView = z.infer<typeof travelChoiceSchema>;
