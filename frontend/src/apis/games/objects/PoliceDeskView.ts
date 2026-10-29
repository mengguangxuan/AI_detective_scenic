import { z } from 'zod';
import { pointIdSchema } from '../../../objects/PointId';
import { clueIdSchema } from '../../../objects/ClueId';
import { requestIdSchema } from '../../../objects/RequestId';
import { sequenceSchema } from '../../../objects/Sequence';
export const policeDeskSchema = z.object({ point: pointIdSchema, name: z.string().min(1), background: z.string(),
  sharedClues: z.array(clueIdSchema), exchanges: z.array(z.object({ requestId: requestIdSchema, sequence: sequenceSchema,
    evidence: z.array(clueIdSchema).min(1).max(20), reply: z.string() }).strict()),
}).strict();
export type PoliceDeskView = z.infer<typeof policeDeskSchema>;
