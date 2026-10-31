import { z } from 'zod';
import { sessionIdSchema } from '../../../objects/SessionId';
import { sequenceSchema } from '../../../objects/Sequence';
export const savedInvestigationSchema = z.object({
  sessionId: sessionIdSchema, caseId: z.string().min(1), title: z.string(),
  revision: z.object({ value: z.number().int().positive() }).strict().transform(value => value.value),
  sequence: sequenceSchema, updatedAtMillis: z.number().int().positive().safe().nullable(),
  status: z.enum(['Investigating', 'Established', 'Solved', 'Revealed', 'LegacySubmitted']),
}).strict();
export type SavedInvestigationView = z.infer<typeof savedInvestigationSchema>;
export const savedInvestigationLabels: Record<SavedInvestigationView['status'], string> = {
  Investigating: '调查进行中', Established: '推理成立，待确认结案', Solved: '已破案',
  Revealed: '已查看解答，未计为破案', LegacySubmitted: '历史推理已提交',
};
