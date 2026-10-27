import { z } from 'zod';
import { sessionIdSchema } from '../../../objects/SessionId';
import { sequenceSchema } from '../../../objects/Sequence';
import { sceneObservationSchema } from './SceneObservation';
import { inquiryPointSchema } from './InquiryPointView';
import { clueViewSchema } from './ClueView';
import { investigationLogSchema } from './InvestigationLogEntry';
export const investigationSnapshotSchema = z.object({
  sessionId: sessionIdSchema, caseId: z.string().min(1), caseRevision: z.object({ value: z.number().int().positive().safe() }).strict().transform(v => v.value),
  sequence: sequenceSchema, title: z.string(),
  scene: z.object({ bundleId: z.literal('study'), revision: z.literal('2'), placeName: z.string(), drawerOpened: z.boolean(), letterVisible: z.boolean() }).strict(),
  points: z.array(inquiryPointSchema), clues: z.array(clueViewSchema), journal: z.array(investigationLogSchema), observations: z.array(sceneObservationSchema),
}).strict().superRefine((snapshot, context) => {
  for (const values of [snapshot.points.map(v => v.id), snapshot.clues.map(v => v.id), snapshot.journal.map(v => v.id), snapshot.observations.map(v => v.node)])
    if (new Set<string>(values).size !== values.length) context.addIssue({ code: 'custom', message: '响应编号重复' });
  if (snapshot.journal.some(v => v.sequence > snapshot.sequence)) context.addIssue({ code: 'custom', message: '日志超出当前序号' });
});
export type InvestigationSnapshot = z.infer<typeof investigationSnapshotSchema>;
