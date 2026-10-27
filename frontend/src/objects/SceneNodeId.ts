import { z } from 'zod';
export const sceneNodeIdSchema = z.string().min(1).max(200).refine(value => value.trim() === value).brand<'SceneNodeId'>();
export type SceneNodeId = z.infer<typeof sceneNodeIdSchema>;
