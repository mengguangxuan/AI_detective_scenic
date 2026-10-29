import { z } from 'zod';
export const timeAssessmentSchema = z.enum(['consistent', 'conflict', 'uncertain']);
export type TimeAssessment = z.infer<typeof timeAssessmentSchema>;
