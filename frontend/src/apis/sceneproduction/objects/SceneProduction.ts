import {z} from 'zod';
export const sceneAuthorInputSchema=z.object({title:z.string().min(1).max(120),targetPlaceId:z.string().regex(/^[a-zA-Z0-9_-]{1,80}$/),fields:z.record(z.unknown())}).strict();
export type SceneAuthorInput=z.infer<typeof sceneAuthorInputSchema>;
const count=z.number().int().nonnegative();
const layoutSchema=z.object({title:z.string(),decisions:z.array(z.string()),items:z.array(z.object({id:z.string(),label:z.string(),x:z.number().finite(),z:z.number().finite(),width:z.number().positive(),depth:z.number().positive(),rotationY:z.number().finite()}).strict())}).strict();
export const sceneProductionSchema=z.object({id:z.string().uuid(),input:sceneAuthorInputSchema,phase:z.enum(['Draft','Queued','Saved']),createdAt:z.string(),savedAt:z.string().nullable(),status:z.object({stage:z.string(),label:z.string(),message:z.string(),round:count,maxRounds:count,calls:count,tokens:count,progress:z.object({generationCalls:count,repairAttempts:count,runtimeCheckAttempts:count,visualReviewAttempts:count,overallReviewAttempts:count}).strict(),issues:z.array(z.object({message:z.string(),severity:z.string()}).strict()),layout:layoutSchema.nullable()}).strict(),previewUrl:z.string().regex(/^http:\/\/127\.0\.0\.1:\d{1,5}\/author-preview\?token=[0-9a-f]{64}$/).nullable()}).strict();
export type SceneProduction=z.infer<typeof sceneProductionSchema>;
