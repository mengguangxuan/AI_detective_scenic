/** @typedef {import('./types').MotionSpec} MotionSpec */
const finite=n=>typeof n==='number'&&Number.isFinite(n);
export const isVec3=v=>v&&['x','y','z'].every(k=>finite(v[k]));
export const isRotation=v=>v&&v.order==='XYZ'&&['xRad','yRad','zRad'].every(k=>finite(v[k]));
const vec=v=>({x:v.x,y:v.y,z:v.z});
const rotation=o=>({xRad:o.rotation.x,yRad:o.rotation.y,zRad:o.rotation.z,order:o.rotation.order});
export function motionIssues(m,nodes){
 const issues=[];
 const add=(code,message,expected,actual)=>issues.push({stage:'protocol',severity:'blocking',code,message,target:{motionId:m?.id,nodeId:m?.targetNodeId},expected,actual,files:['assembler']});
 if(!m||typeof m.id!=='string'||!nodes[m.targetNodeId]){add('motion_reference','动作必须引用已登记节点','MotionSpec.targetNodeId',m??null);return issues;}
 const o=nodes[m.targetNodeId];
 if(m.space!=='parent-local'||!['closed','opened'].includes(m.initialState)||!finite(m.durationMs)||m.durationMs<0){add('motion_header','动作坐标系、初态或时长无效',{space:'parent-local',initialState:['closed','opened'],durationMs:'finite >= 0'},m);return issues;}
 if(m.kind==='translation'){
  if(!isVec3(m.closedPositionMeters)||!isVec3(m.openedPositionMeters)){add('translation_endpoint','平移动作需要米制位置对象',{closedPositionMeters:'{x,y,z}',openedPositionMeters:'{x,y,z}'},m);return issues;}
  const expected=m.initialState==='closed'?m.closedPositionMeters:m.openedPositionMeters,actual=vec(o.position);
  if(Math.hypot(actual.x-expected.x,actual.y-expected.y,actual.z-expected.z)>.001)add('translation_initial_pose','初始位置与平移动作初态不一致',expected,{positionMeters:actual,parent:o.parent?.name??null});
 }else if(m.kind==='rotation'){
  if(!isRotation(m.closedRotation)||!isRotation(m.openedRotation)){add('rotation_endpoint','旋转动作需要XYZ顺序的弧度角对象',{closedRotation:'{xRad,yRad,zRad,order:"XYZ"}',openedRotation:'same'},m);return issues;}
  const expected=m.initialState==='closed'?m.closedRotation:m.openedRotation;
  const expectedEuler=o.rotation.clone().set(expected.xRad,expected.yRad,expected.zRad,'XYZ');
  const q=o.quaternion.clone().setFromEuler(expectedEuler);
  if(o.quaternion.angleTo(q)>.001)add('rotation_initial_pose','初始旋转与旋转动作初态不一致',expected,{rotation:rotation(o),positionMeters:vec(o.position),parent:o.parent?.name??null});
 }else add('motion_kind','动作必须明确区分translation与rotation',['translation','rotation'],m.kind??null);
 return issues;
}
export function applyMotion(m,state,nodes){
 if(!['closed','opened'].includes(state))throw new Error('Invalid motion target state');
 const o=nodes[m.targetNodeId];if(!o)throw new Error('Missing motion target '+m.targetNodeId);
 if(m.kind==='translation'){
  const v=state==='closed'?m.closedPositionMeters:m.openedPositionMeters;if(!isVec3(v))throw new Error('Invalid translation endpoint');o.position.set(v.x,v.y,v.z);
 }else if(m.kind==='rotation'){
  const v=state==='closed'?m.closedRotation:m.openedRotation;if(!isRotation(v))throw new Error('Invalid rotation endpoint');o.rotation.set(v.xRad,v.yRad,v.zRad,v.order);
 }else throw new Error('Unsupported motion kind');
}
export function definitionIssues(THREE,result){
 const issues=[];
 const add=(code,message,target={},expected=null,actual=null)=>issues.push({stage:'protocol',severity:'blocking',code,message,target,expected,actual,files:['assembler']});
 if(!(result?.root instanceof THREE.Group)||!result.nodeObjects||!result.definition){add('assembly_shape','组装器必须返回root、nodeObjects、definition');return issues;}
 const d=result.definition,nodes=result.nodeObjects;
 if(['nodes','bindings','anchors','motions','requirements','viewpoints'].some(k=>!Array.isArray(d[k]))){add('definition_shape','definition缺少规定的数组字段');return issues;}
 result.root.updateMatrixWorld(true);const members=new Set();result.root.traverse(o=>members.add(o));
 if(result.root.position.length()>.001||result.root.scale.distanceTo(new THREE.Vector3(1,1,1))>.001||result.root.quaternion.angleTo(new THREE.Quaternion())>.001)add('root_transform','场景根必须保持单位姿态');
 const ids=new Set(),objectIds=new Map();for(const [id,o]of Object.entries(nodes)){
  if(!members.has(o)||o===result.root){add('node_reference','节点必须是场景根的后代',{nodeId:id});continue;}
  if(objectIds.has(o))add('duplicate_object','同一个对象不能登记为多个节点',{nodeId:id});objectIds.set(o,id);
 }
 for(const n of d.nodes){
  const o=nodes[n.id];if(ids.has(n.id))add('duplicate_node','节点ID重复',{nodeId:n.id});ids.add(n.id);
  if(!o){add('node_missing','声明节点缺少真实对象',{nodeId:n.id});continue;}
  const expectedParent=n.parentId===null?result.root:nodes[n.parentId];
  if(!expectedParent||o.parent!==expectedParent)add('node_parent','实际父节点与声明不一致',{nodeId:n.id},n.parentId,objectIds.get(o.parent)??null);
  const t=n.initialTransform;
  if(!t||!isVec3(t.positionMeters)||!isRotation(t.rotation)||!isVec3(t.scale)||Object.values(t.scale).some(v=>v<=0)){add('node_transform_shape','节点姿态类型无效',{nodeId:n.id});continue;}
  const v=t.positionMeters,r=t.rotation,sc=t.scale;
  const q=new THREE.Quaternion().setFromEuler(new THREE.Euler(r.xRad,r.yRad,r.zRad,r.order));
  if(o.position.distanceTo(new THREE.Vector3(v.x,v.y,v.z))>.001||o.quaternion.angleTo(q)>.001||o.scale.distanceTo(new THREE.Vector3(sc.x,sc.y,sc.z))>.001)add('node_initial_pose','节点实际姿态与声明不一致',{nodeId:n.id},t,{positionMeters:vec(o.position),rotation:rotation(o),scale:vec(o.scale)});
 }
 for(const id of Object.keys(nodes))if(!ids.has(id))add('node_undeclared','对象登记缺少节点声明',{nodeId:id});
 for(const b of d.bindings)if(!ids.has(b.nodeId)||typeof b.entityId!=='string')add('binding_reference','实体绑定引用无效',{nodeId:b.nodeId});
 const anchorIds=new Set();for(const a of d.anchors){if(anchorIds.has(a.id)||!ids.has(a.nodeId)||!isVec3(a.pointMeters))add('anchor_reference','锚点ID、节点或坐标无效',{nodeId:a.nodeId});anchorIds.add(a.id);}
 const motionIds=new Set(),channels=new Set();for(const m of d.motions){if(motionIds.has(m.id))add('duplicate_motion','动作ID重复',{motionId:m.id});motionIds.add(m.id);const channel=m.targetNodeId+':'+m.kind;if(channels.has(channel))add('duplicate_channel','同一节点同一通道只允许一个动作',{motionId:m.id});channels.add(channel);issues.push(...motionIssues(m,nodes));}
 for(const req of d.requirements)if(typeof req.description!=='string'||req.checkBy!=='visual'||!Array.isArray(req.relatedNodeIds)||req.relatedNodeIds.some(id=>!ids.has(id)))add('requirement_reference','视觉要求引用无效');
 if(d.viewpoints.length<4)add('view_count','需要至少四个有效视角');
 const viewIds=new Set();for(const v of d.viewpoints){
  if(viewIds.has(v.id)||!isVec3(v.cameraWorldPositionMeters)||!isVec3(v.cameraWorldTargetMeters)||!Array.isArray(v.hiddenNodeIds)||!Array.isArray(v.motionStates)){add('view_shape','视角字段无效');continue;}viewIds.add(v.id);
  if(v.hiddenNodeIds.some(id=>!ids.has(id))||v.motionStates.some(s=>!motionIds.has(s.motionId)||!['closed','opened'].includes(s.state)))add('view_reference','视角引用无效');
  const p=v.cameraWorldPositionMeters,t=v.cameraWorldTargetMeters;if(Math.hypot(p.x-t.x,p.y-t.y,p.z-t.z)<.1)add('view_direction','相机与观察目标过近');
 }
 if(!d.appearance||!/^#[0-9a-fA-F]{6}$/.test(d.appearance.backgroundColor)||!Number.isFinite(d.appearance.exposure)||d.appearance.exposure<=0)add('appearance_shape','需要明确背景色与正数曝光');
 return issues;
}
export function planIssues(p){
 const issues=[];
 if(!p||!Array.isArray(p.modules)||p.modules.length<2||p.modules.length>12)return ['modules必须有2至12个完整模块；实际数量：'+(p?.modules?.length??'缺失')];
 const ids=new Set();
 for(const m of p.modules){
  if(typeof m.id!=='string'||!/^[a-z][a-z0-9_]{0,45}$/.test(m.id)||ids.has(m.id))issues.push('模块ID非法或重复：'+m.id);ids.add(m.id);
  if(typeof m.task!=='string'||!Array.isArray(m.specs)||!m.specs.length){issues.push('模块缺少task/specs：'+m.id);continue;}
  for(const spec of m.specs)if(!spec||spec.parameters===undefined||!spec.sizeBudget||['widthMeters','heightMeters','depthMeters'].some(k=>!Number.isFinite(spec.sizeBudget[k])||spec.sizeBudget[k]<=0))issues.push('ModuleBuildInput参数或sizeBudget无效：'+m.id);
 }
 if(!Array.isArray(p.instances)||!p.instances.length||p.instances.length>40)return [...issues,'instances必须有1至40个实例'];
 const instances=new Set();for(const i of p.instances){
  const m=p.modules.find(m=>m.id===i.module);
  if(typeof i.id!=='string'||instances.has(i.id)||!m||!Number.isInteger(i.specIndex)||!m.specs?.[i.specIndex])issues.push('实例ID/模块/specIndex无效：'+i.id);instances.add(i.id);
  if(!isVec3(i.placement?.positionMeters)||!isRotation(i.placement?.rotation)||!isVec3(i.placement?.scale)||Object.values(i.placement?.scale??{}).some(v=>v<=0))issues.push('placement必须使用米制坐标、XYZ弧度和正数缩放：'+i.id);
 }
 return issues;
}
