import {sendMessage} from '../../apiClient/sendMessage';
import {sceneProductionSchema} from './objects/SceneProduction';
export class SaveSceneArtifactAPI {
  constructor(readonly productionId:string) {}
  send() {return sendMessage(this.constructor.name,{productionId:this.productionId},sceneProductionSchema);}
}
