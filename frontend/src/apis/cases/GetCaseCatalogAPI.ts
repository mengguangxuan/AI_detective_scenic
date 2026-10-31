import { z } from 'zod';
import { sendMessage } from '../../apiClient/sendMessage';
import { caseCatalogEntrySchema } from './objects/CaseCatalogEntry';
export class GetCaseCatalogAPI {
  send() { return sendMessage(this.constructor.name, {}, z.array(caseCatalogEntrySchema)); }
}
