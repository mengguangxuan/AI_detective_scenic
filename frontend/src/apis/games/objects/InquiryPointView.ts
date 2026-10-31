import { z } from 'zod';
import { pointIdSchema } from '../../../objects/PointId';
import { operationIdSchema } from '../../../objects/OperationId';
import { routeIdSchema } from '../../../objects/RouteId';
import { clueViewSchema } from './ClueView';
import { clueIdSchema } from '../../../objects/ClueId';
import { sequenceSchema } from '../../../objects/Sequence';
export const inquiryPointSchema = z.object({
  id: pointIdSchema, label: z.string(),
  operations: z.array(z.object({ id: operationIdSchema, label: z.string() }).strict()),
  routes: z.array(z.object({ id: routeIdSchema, label: z.string() }).strict()),
  findings: z.array(clueViewSchema),
  presentations: z.array(z.object({ sequence: sequenceSchema, evidence: z.array(clueIdSchema), reply: z.string() }).strict()).default([]),
}).strict();
export type InquiryPointView = z.infer<typeof inquiryPointSchema>;
