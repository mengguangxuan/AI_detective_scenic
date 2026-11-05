import {z} from 'zod';
import {sendMessage} from '../../apiClient/sendMessage';
import {sceneProductionSchema} from './objects/SceneProduction';
export class ListSceneProductionsAPI {
  send(){return sendMessage(this.constructor.name,{},z.array(sceneProductionSchema));}
}
