import {sendMessage} from '../../apiClient/sendMessage';
import {sceneProductionSchema} from './objects/SceneProduction';
export class ResumeSceneProductionAPI {
 constructor(readonly productionId:string,readonly expectedCalls:number){}
 send(){return sendMessage(this.constructor.name,{productionId:this.productionId,expectedCalls:this.expectedCalls},sceneProductionSchema);}
}
