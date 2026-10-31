import { z } from 'zod';
export const caseCatalogEntrySchema = z.object({
  caseId: z.string().min(1), revision: z.object({ value: z.number().int().positive() }).strict().transform(value => value.value),
  title: z.string(), summary: z.string(), tags: z.array(z.string()),
}).strict();
export type CaseCatalogEntry = z.infer<typeof caseCatalogEntrySchema>;
