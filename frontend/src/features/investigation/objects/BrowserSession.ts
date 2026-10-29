import { z } from 'zod';
import { sessionIdSchema } from '../../../objects/SessionId';
import { pendingCommandSchema } from './PendingCommand';
export const browserSessionSchema = z.object({
  version: z.literal(1), sessionId: sessionIdSchema, creationPending: z.boolean(),
  pending: z.object({ command: pendingCommandSchema, resultSequence: z.number().int().nonnegative().safe().nullable() }).strict().nullable(),
}).strict().refine(v => v.pending === null || v.pending.command.sessionId === v.sessionId, '请求不属于此会话');
export type BrowserSession = z.infer<typeof browserSessionSchema>;
