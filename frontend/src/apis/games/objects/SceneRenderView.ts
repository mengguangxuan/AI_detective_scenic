import { z } from 'zod';
import { sceneNodeIdSchema } from '../../../objects/SceneNodeId';
const vector = z.object({ x: z.number().finite(), y: z.number().finite(), z: z.number().finite() }).strict();
const size = z.object({ x: z.number().positive().max(20), y: z.number().positive().max(20), z: z.number().positive().max(20) }).strict();
export const sceneRenderSchema = z.object({
  room: z.object({ place: z.string().min(1), description: z.string().min(1).max(2000), floorSize: size,
    color: z.string().regex(/^#[0-9a-fA-F]{6}$/), cameraPosition: vector, cameraTarget: vector }).strict(),
  instances: z.array(z.object({ node: sceneNodeIdSchema, position: vector,
    parts: z.array(z.object({ shape: z.enum(['Box', 'Cylinder', 'Sphere']), size, offset: vector, color: z.string().regex(/^#[0-9a-fA-F]{6}$/) }).strict()).min(1).max(20) }).strict()).max(100),
}).strict();
export type SceneRenderView = z.infer<typeof sceneRenderSchema>;
