import { z } from 'zod';
import { witnessViewSchema } from './WitnessView';
import { interviewTurnSchema } from './InterviewTurnView';
import { policeDeskSchema } from './PoliceDeskView';
import { casePersonBriefSchema } from './CasePersonBrief';
import { sessionIdSchema } from '../../../objects/SessionId';
import { sequenceSchema } from '../../../objects/Sequence';
import { sceneObservationSchema } from './SceneObservation';
import { inquiryPointSchema } from './InquiryPointView';
import { clueViewSchema } from './ClueView';
import { investigationLogSchema } from './InvestigationLogEntry';
import { sceneObjectBindingSchema } from './SceneObjectBinding';
export const investigationSnapshotSchema = z.object({
  sessionId: sessionIdSchema, caseId: z.string().min(1), caseRevision: z.object({ value: z.number().int().positive().safe() }).strict().transform(v => v.value),
  sequence: sequenceSchema, title: z.string(),
  scene: z.object({ bundleId: z.literal('study'), revision: z.literal('4'), placeName: z.string(), drawerOpened: z.boolean(), windowOpened: z.boolean(), objects: z.array(sceneObjectBindingSchema) }).strict(),
  points: z.array(inquiryPointSchema), clues: z.array(clueViewSchema), journal: z.array(investigationLogSchema), observations: z.array(sceneObservationSchema),
  witnesses: z.array(witnessViewSchema), interviews: z.array(interviewTurnSchema),
  police: policeDeskSchema.nullable(),
  people: z.array(casePersonBriefSchema),
  stage: z.object({ introduction: z.string(), objective: z.string(), completed: z.boolean(), nextObjective: z.string().nullable() }).strict().nullable(),
}).strict().superRefine((snapshot, context) => {
  for (const values of [snapshot.people.map(v => v.id), snapshot.points.map(v => v.id), snapshot.clues.map(v => v.id), snapshot.journal.map(v => v.id), snapshot.observations.map(v => v.node), snapshot.scene.objects.map(v => v.node), snapshot.scene.objects.map(v => v.entity), snapshot.witnesses.map(v => v.id), snapshot.interviews.map(v => v.requestId)])
    if (new Set<string>(values).size !== values.length) context.addIssue({ code: 'custom', message: '响应编号重复' });
  if (snapshot.journal.some(v => v.sequence > snapshot.sequence)) context.addIssue({ code: 'custom', message: '日志超出当前序号' });
  if (snapshot.people.some(person => person.witness !== null && !snapshot.witnesses.some(witness => witness.id === person.witness)))
    context.addIssue({ code: 'custom', message: '公开人物绑定的问询对象不可用' });
  if (snapshot.interviews.some(t => t.sequence > snapshot.sequence || !snapshot.witnesses.some(w => w.id === t.witness) || t.shownClues.some(id => !snapshot.clues.some(c => c.id === id))))
    context.addIssue({ code: 'custom', message: '对话引用超出当前视图' });
  if (snapshot.police) {
    const police = snapshot.police;
    const cited = new Set(police.exchanges.flatMap(exchange => exchange.evidence));
    if (!snapshot.points.some(point => point.id === police.point) || police.sharedClues.some(id => !snapshot.clues.some(clue => clue.id === id)) ||
      new Set(police.sharedClues).size !== police.sharedClues.length || new Set(police.exchanges.map(exchange => exchange.requestId)).size !== police.exchanges.length ||
      cited.size !== police.sharedClues.length || police.sharedClues.some(id => !cited.has(id)) ||
      police.exchanges.some(exchange => exchange.sequence > snapshot.sequence || new Set(exchange.evidence).size !== exchange.evidence.length || exchange.evidence.some(id => !police.sharedClues.includes(id))))
      context.addIssue({ code: 'custom', message: '警方交流引用超出当前视图' });
  }
  for (const object of snapshot.scene.objects) {
    if (object.point ? !snapshot.points.some(p => p.id === object.point) : !snapshot.observations.some(o => o.node === object.node))
      context.addIssue({ code: 'custom', message: '物品未绑定可用入口或外观' });
  }
  for (const point of snapshot.points) {
    if (point.findings.some(f => !snapshot.clues.some(c => c.id === f.id && c.text === f.text && c.title === f.title)))
      context.addIssue({ code: 'custom', message: '物品正文超出已获材料' });
  }
});
export type InvestigationSnapshot = z.infer<typeof investigationSnapshotSchema>;
