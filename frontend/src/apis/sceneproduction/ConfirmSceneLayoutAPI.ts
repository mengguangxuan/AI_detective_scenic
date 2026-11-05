import {sendMessage} from '../../apiClient/sendMessage';
import {sceneProductionSchema} from './objects/SceneProduction';
export class ConfirmSceneLayoutAPI {
  constructor(readonly productionId:string) {}
  send() {return sendMessage(this.constructor.name,{productionId:this.productionId},sceneProductionSchema);}
}
