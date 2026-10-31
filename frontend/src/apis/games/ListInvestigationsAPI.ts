import { z } from 'zod';
import { sendMessage } from '../../apiClient/sendMessage';
import { savedInvestigationSchema } from './objects/SavedInvestigationView';
export class ListInvestigationsAPI {
  send() { return sendMessage(this.constructor.name, {}, z.array(savedInvestigationSchema)); }
}
