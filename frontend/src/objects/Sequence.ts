import { z } from 'zod';
export const sequenceSchema = z.object({ value: z.number().int().nonnegative().safe() }).strict().transform(value => value.value);
