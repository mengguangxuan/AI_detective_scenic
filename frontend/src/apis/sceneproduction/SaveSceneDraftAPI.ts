import {sendMessage} from '../../apiClient/sendMessage';
import {sceneProductionSchema,type SceneAuthorInput} from './objects/SceneProduction';
export class SaveSceneDraftAPI {
  constructor(readonly productionId:string,readonly input:SceneAuthorInput) {}
  send(){return sendMessage(this.constructor.name,{productionId:this.productionId,input:this.input},sceneProductionSchema);}
}
