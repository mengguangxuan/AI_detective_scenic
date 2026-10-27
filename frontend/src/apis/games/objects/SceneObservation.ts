import { z } from 'zod';
import { sceneNodeIdSchema } from '../../../objects/SceneNodeId';
export const sceneObservationSchema = z.object({ node: sceneNodeIdSchema, label: z.string(), description: z.string() }).strict();
export type SceneObservation = z.infer<typeof sceneObservationSchema>;
