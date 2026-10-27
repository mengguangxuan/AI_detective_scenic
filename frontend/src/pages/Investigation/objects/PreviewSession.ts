import { z } from 'zod';

// Only the local preview protocol is closed; this is not an enumeration of case content.
export const previewSessionSchema = z.object({
  version: z.literal(1),
  sequence: z.number().int().min(0).max(2),
  drawerOpened: z.boolean(),
  letterRead: z.boolean(),
}).strict().refine(s => s.sequence === Number(s.drawerOpened) + Number(s.letterRead)
  && (!s.letterRead || s.drawerOpened), '预览进度不一致');
export type PreviewSession = z.infer<typeof previewSessionSchema>;
