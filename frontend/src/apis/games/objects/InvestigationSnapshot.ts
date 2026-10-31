import { reasoningProgressSchema } from './ReasoningProgress';
import { caseConclusionSchema } from './CaseConclusionRecord';
import { personIdSchema } from '../../../objects/PersonId';
import { z } from 'zod';
import { witnessFollowUpSchema } from './WitnessFollowUpView';
import { statementComparisonSchema } from './StatementComparisonView';
import { areaMapSchema } from './AreaMap';
import { externalCheckViewSchema } from './ExternalCheckView';
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
  reasoning: reasoningProgressSchema.nullable().default(null),
  conclusionCandidates: z.array(personIdSchema).default([]),
  conclusion: caseConclusionSchema.nullable().default(null),
  followUps: z.array(witnessFollowUpSchema).default([]),
  statementComparisons: z.array(statementComparisonSchema).default([]),
  areaMap: areaMapSchema.nullable().default(null),
  clock: z.object({ label: z.string(), elapsedSeconds: z.number().int().nonnegative().safe(), actionMinutes: z.number().int().positive() }).strict().nullable().default(null),
  externalChecks: z.array(externalCheckViewSchema).default([]),
  sessionId: sessionIdSchema, caseId: z.string().min(1), caseRevision: z.object({ value: z.number().int().positive().safe() }).strict().transform(v => v.value),
  sequence: sequenceSchema, title: z.string(),
  scene: z.object({ bundleId: z.literal('study'), revision: z.enum(['4', '5']), placeName: z.string(), drawerOpened: z.boolean(), windowOpened: z.boolean(), objects: z.array(sceneObjectBindingSchema) }).strict(),
  points: z.array(inquiryPointSchema), clues: z.array(clueViewSchema), journal: z.array(investigationLogSchema), observations: z.array(sceneObservationSchema),
  witnesses: z.array(witnessViewSchema), interviews: z.array(interviewTurnSchema),
  police: policeDeskSchema.nullable(),
  people: z.array(casePersonBriefSchema),
  stage: z.object({ introduction: z.string(), objective: z.string(), completed: z.boolean(), nextObjective: z.string().nullable() }).strict().nullable(),
}).strict().superRefine((snapshot, context) => {
  if (snapshot.reasoning) {
    const attempts = snapshot.reasoning.attempts;
    if (new Set(attempts.map(a => a.requestId)).size !== attempts.length || attempts.some((a, i) =>
      a.sequence > snapshot.sequence || (i > 0 && a.sequence <= attempts[i - 1]!.sequence) ||
      a.suspects.some(id => !snapshot.conclusionCandidates.includes(id)) || a.evidence.some(id => !snapshot.clues.some(c => c.id === id)) ||
      (a.status === 'Established' && (!a.checks.length || a.checks.some(c => !c.satisfied))) ||
      (['Unavailable', 'Rejected'].includes(a.status) && a.checks.length !== 0)))
      context.addIssue({ code: 'custom', message: '推理评审记录与当前调查不一致' });
    const ending = snapshot.reasoning.ending;
    if (ending && (ending.sequence > snapshot.sequence || (attempts.length && ending.sequence <= attempts.at(-1)!.sequence) ||
      (ending.mode === 'Solved' && attempts.at(-1)?.status !== 'Established')))
      context.addIssue({ code: 'custom', message: '推理收尾与已保存评审不一致' });
    if (ending && (new Set(ending.sections.map(s => s.title)).size !== ending.sections.length || ending.sections.some(s =>
      new Set(s.materials.map(m => m.id)).size !== s.materials.length || s.materials.some(m =>
        !snapshot.clues.some(c => c.id === m.id && c.title === m.title && c.text === m.text) ||
        m.cited !== (attempts.at(-1)?.evidence.includes(m.id) ?? false)))))
      context.addIssue({ code: 'custom', message: '结案对照超出已获材料或与末次引用不一致' });
  }
  if (snapshot.conclusionCandidates.some(id => !snapshot.people.some(p => p.id === id))) context.addIssue({ code: 'custom', message: '推理人物不在公开资料中' });
  const result = snapshot.conclusion;
  if (result && (result.sequence > snapshot.sequence || result.suspects.some(id => !snapshot.conclusionCandidates.includes(id)) ||
    result.evidence.some(id => !snapshot.clues.some(c => c.id === id)) || result.evidenceScore !== result.coverage.filter(g => g.covered).length * 10 ||
    result.coverage.some(g => g.covered !== g.requiredClues.every(id => result.evidence.includes(id)))))
    context.addIssue({ code: 'custom', message: '推理复盘与提交记录不一致' });
  for (const values of [snapshot.followUps.map(v => v.operation), snapshot.statementComparisons.map(v => v.witness), snapshot.externalChecks.map(v => v.id), snapshot.people.map(v => v.id), snapshot.points.map(v => v.id), snapshot.clues.map(v => v.id), snapshot.journal.map(v => v.id), snapshot.observations.map(v => v.node), snapshot.scene.objects.map(v => v.node), snapshot.scene.objects.map(v => v.entity), snapshot.witnesses.map(v => v.id), snapshot.interviews.map(v => v.requestId)])
    if (new Set<string>(values).size !== values.length) context.addIssue({ code: 'custom', message: '响应编号重复' });
  for (const followUp of snapshot.followUps) {
    const witness = snapshot.witnesses.find(item => item.id === followUp.witness);
    const point = snapshot.points.find(item => item.id === witness?.point);
    if (!point || (followUp.started && followUp.ready) || (followUp.ready && !point.operations.some(item => item.id === followUp.operation)) || followUp.questions.some(question => !point.routes.some(route => route.id === question.id)))
      context.addIssue({ code: 'custom', message: '补充问询状态与当前入口不一致' });
  }
  for (const comparison of snapshot.statementComparisons) {
    if (!snapshot.witnesses.some(witness => witness.id === comparison.witness) || comparison.rows.some(row => [row.initial, row.revised].some(version => version && (version.sequence > snapshot.sequence || !snapshot.clues.some(clue => clue.id === version.clue.id && clue.text === version.clue.text && clue.title === version.clue.title)))))
      context.addIssue({ code: 'custom', message: '供述对照超出实际已获记录' });
  }
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
