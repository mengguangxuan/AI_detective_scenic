import {useCallback,useEffect,useRef,useState} from 'react';
import {OpenLocalIdentityAPI} from '../../../apis/accounts/OpenLocalIdentityAPI';
import {ListSceneProductionsAPI} from '../../../apis/sceneproduction/ListSceneProductionsAPI';
import {GetSceneProductionAPI} from '../../../apis/sceneproduction/GetSceneProductionAPI';
import {SaveSceneDraftAPI} from '../../../apis/sceneproduction/SaveSceneDraftAPI';
import {ResumeSceneProductionAPI} from '../../../apis/sceneproduction/ResumeSceneProductionAPI';
import {StartSceneProductionAPI} from '../../../apis/sceneproduction/StartSceneProductionAPI';
import {ConfirmSceneLayoutAPI} from '../../../apis/sceneproduction/ConfirmSceneLayoutAPI';
import {SaveSceneArtifactAPI} from '../../../apis/sceneproduction/SaveSceneArtifactAPI';
import type {SceneAuthorInput,SceneProduction} from '../../../apis/sceneproduction/objects/SceneProduction';
const locatorKey='detective-scenic:scene-production';
const stopped=new Set(['draft','passed','needs_attention','needs_input','failed']);
type LoadState={kind:'loading'}|{kind:'failed';message:string}|{kind:'ready'};
export function useSceneProduction(){
 const [state,setState]=useState<LoadState>({kind:'loading'});
 const [entries,setEntries]=useState<SceneProduction[]>([]);
 const [selected,setSelected]=useState<SceneProduction|null>(null);
 const [busy,setBusy]=useState(false),[notice,setNotice]=useState('');
 const selectedRef=useRef<string|null>(null),action=useRef(false);
 const put=useCallback((row:SceneProduction)=>{setEntries(old=>[row,...old.filter(item=>item.id!==row.id)]);if(selectedRef.current===row.id)setSelected(row);},[]);
 const choose=useCallback((row:SceneProduction|null)=>{selectedRef.current=row?.id??null;setSelected(row);try{if(row)localStorage.setItem(locatorKey,row.id);else localStorage.removeItem(locatorKey);}catch{/* Server data remains authoritative. */}},[]);
 const reload=useCallback(async()=>{setState({kind:'loading'});try{await new OpenLocalIdentityAPI().send();const rows=await new ListSceneProductionsAPI().send();setEntries(rows);let saved:string|null=null;try{saved=localStorage.getItem(locatorKey);}catch{}const found=rows.find(r=>r.id===saved);if(found){choose(found);void new GetSceneProductionAPI(found.id).send().then(put).catch(()=>{});}setState({kind:'ready'});}catch(error){setState({kind:'failed',message:error instanceof Error?error.message:'读取失败，请重试。'});}},[choose,put]);
 useEffect(()=>{void reload();},[reload]);
 const id=selected?.id,stage=selected?.status.stage;
 useEffect(()=>{if(!id||!stage||stopped.has(stage)&&!(stage==='passed'&&!selected?.previewUrl))return;let active=true;const timer=setInterval(()=>{void new GetSceneProductionAPI(id).send().then(row=>{if(active)put(row);}).catch(error=>{if(active)setNotice(error instanceof Error?error.message:'无法读取制作进度。');});},2500);return()=>{active=false;clearInterval(timer);};},[id,stage,selected?.previewUrl,put]);
 async function perform(run:()=>Promise<SceneProduction>){if(action.current)return null;action.current=true;setBusy(true);setNotice('');try{const row=await run();choose(row);put(row);return row;}catch(error){setNotice(error instanceof Error?error.message:'操作结果尚未确认，请保留原草稿重试。');return null;}finally{action.current=false;setBusy(false);}}
 return{state,entries,selected,busy,notice,choose,reload,
  save:(productionId:string,input:SceneAuthorInput)=>perform(()=>new SaveSceneDraftAPI(productionId,input).send()),
  resume:()=>selected?perform(()=>new ResumeSceneProductionAPI(selected.id,selected.status.calls).send()):Promise.resolve(null),
  refresh:()=>selected?perform(()=>new GetSceneProductionAPI(selected.id).send()):Promise.resolve(null),
  start:()=>selected?perform(()=>new StartSceneProductionAPI(selected.id).send()):Promise.resolve(null),
  confirm:()=>selected?perform(()=>new ConfirmSceneLayoutAPI(selected.id).send()):Promise.resolve(null),
  saveArtifact:()=>selected?perform(()=>new SaveSceneArtifactAPI(selected.id).send()):Promise.resolve(null)};
}
