import { z } from 'zod';
import { sendMessage } from '../../apiClient/sendMessage';
const responseSchema = z.object({ mode: z.literal('local-demo') }).strict();
export class OpenLocalIdentityAPI {
  send() { return sendMessage(this.constructor.name, {}, responseSchema); }
}
