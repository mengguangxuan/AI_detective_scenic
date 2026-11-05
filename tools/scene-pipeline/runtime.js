import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {makeKit} from './kit.js';
import {assembleScene} from './assembler.js';
import {definitionIssues,applyMotion} from './contract.js';
const inspectMode=location.pathname==='/inspect';
window.pipelineReady=false;
try {
 const plan=await fetch('/plan.json').then(r=>r.json());
 const modules=Object.fromEntries(await Promise.all(plan.modules.map(async m=>[m.id,await import('/modules/'+m.id+'.js')])));
 const result=assembleScene(THREE,modules,plan,makeKit(THREE));
 const issues=definitionIssues(THREE,result);window.pipelineIssues=issues;
 if(issues.length&&!inspectMode)throw new Error(issues.map(i=>i.code+': '+i.message).join('; '));
 if(!result?.definition||!result.nodeObjects||!Array.isArray(result.definition.viewpoints))throw new Error('Missing typed scene definition');
 const d=result.definition,nodes=result.nodeObjects;
 const scene=new THREE.Scene();scene.background=new THREE.Color(d.appearance?.backgroundColor??'#202323');scene.add(result.root);
 const renderer=new THREE.WebGLRenderer({antialias:true});renderer.setPixelRatio(1);renderer.setSize(innerWidth,innerHeight);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=d.appearance?.exposure??1;renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;document.body.appendChild(renderer.domElement);
 const camera=new THREE.PerspectiveCamera(55,innerWidth/innerHeight,.02,300);
 const controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;
 const report={passed:!inspectMode&&!issues.length,inspectionOnly:inspectMode,issues,objects:[],meshes:0,triangles:0};
 result.root.updateMatrixWorld(true);
 result.root.traverse(o=>{if([...o.position.toArray(),...o.scale.toArray(),...o.quaternion.toArray()].some(v=>!Number.isFinite(v)))throw new Error('nonfinite object transform');if(o.isMesh){report.meshes++;const a=o.geometry?.attributes?.position;if(!a)throw new Error('geometry missing positions');for(const v of a.array)if(!Number.isFinite(v))throw new Error('nonfinite geometry');report.triangles+=(o.geometry.index?.count??a.count)/3;o.castShadow=true;o.receiveShadow=true;}});
 if(report.meshes===0||report.meshes>6000||report.triangles>2000000)throw new Error('scene geometry budget exceeded or empty');
 for(const [id,o]of Object.entries(nodes)){const b=new THREE.Box3().setFromObject(o);if(!b.isEmpty()){if([...b.min.toArray(),...b.max.toArray()].some(v=>!Number.isFinite(v)))throw new Error('nonfinite binding: '+id);report.objects.push({id,min:b.min.toArray(),max:b.max.toArray()});}}
 for(const b of d.bindings)if(new THREE.Box3().setFromObject(nodes[b.nodeId]).isEmpty())throw new Error('Entity binding has no geometry: '+b.nodeId);
 const motions=new Map(d.motions.map(m=>[m.id,m]));
 const initialVisibility=new Map();result.root.traverse(o=>initialVisibility.set(o,o.visible));
 const labels=document.querySelector('#views');
 function reset(){for(const [o,visible]of initialVisibility)o.visible=visible;if(!inspectMode)for(const m of d.motions)applyMotion(m,m.initialState,nodes);}
 function showView(index){const v=d.viewpoints[index];reset();if(!inspectMode)for(const target of v.motionStates)applyMotion(motions.get(target.motionId),target.state,nodes);for(const id of v.hiddenNodeIds)nodes[id].visible=false;
  const p=v.cameraWorldPositionMeters,t=v.cameraWorldTargetMeters;camera.position.set(p.x,p.y,p.z);controls.target.set(t.x,t.y,t.z);controls.update();document.querySelector('#title').textContent=(inspectMode?'未验收 · 静态查看 · ':'')+v.purpose;renderer.render(scene,camera);
 }
 d.viewpoints.forEach((view,i)=>{const button=document.createElement('button');button.textContent=view.purpose;button.onclick=()=>showView(i);labels.appendChild(button);});
 if(!inspectMode)for(const m of d.motions){const button=document.createElement('button');button.textContent='开关 '+m.id;button.onclick=()=>{const state=button.dataset.open==='yes'?'closed':'opened';applyMotion(m,state,nodes);button.dataset.open=state==='opened'?'yes':'no';};labels.appendChild(button);}
 renderer.setAnimationLoop(()=>{controls.update();renderer.render(scene,camera);});const requested=Number(new URLSearchParams(location.search).get('view')??0);showView(Number.isInteger(requested)&&requested>=0&&requested<d.viewpoints.length?requested:0);
 addEventListener('resize',()=>{camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight);});
 window.pipelineScene={showView,views:d.viewpoints,report,result,renderer};window.pipelineReady=true;
}catch(error){window.pipelineError=error.message;document.querySelector('#title').textContent='生成场景运行失败';console.error(error);}
