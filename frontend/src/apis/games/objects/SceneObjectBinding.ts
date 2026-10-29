import { z } from 'zod';
import { sceneNodeIdSchema } from '../../../objects/SceneNodeId';
import { pointIdSchema } from '../../../objects/PointId';
import { routeIdSchema } from '../../../objects/RouteId';

export const sceneObjectBindingSchema = z.object({
  node: sceneNodeIdSchema, entity: z.string().min(1), label: z.string().min(1),
  point: pointIdSchema.nullable(), readRoute: routeIdSchema.nullable(),
}).strict();
export type SceneObjectBinding = z.infer<typeof sceneObjectBindingSchema>;
