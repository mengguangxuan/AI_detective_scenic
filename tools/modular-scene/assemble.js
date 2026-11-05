// Stable host contract. Generated modules never receive the scene or each other's objects.
export function assemble(THREE,layout,builders,kit) {
  const root=new THREE.Group();root.name='assembled_bedroom';
  const objects={},sockets={},motions={},assets={},reports=[];
  const tolerance=.035;
  const finiteArray=(v,n)=>Array.isArray(v)&&v.length===n&&v.every(Number.isFinite);
  function fail(id,reason){throw new Error(`${id}: ${reason}`);}
  for(const spec of layout.instances){
    const builder=builders[spec.module];if(typeof builder!=='function')fail(spec.id,'missing registered builder');
    const input=structuredClone(spec);
    // Placement is deliberately withheld from generated code.
    delete input.position;delete input.rotationY;
    const value=builder(THREE,input,kit);
    if(!value || !(value.root instanceof THREE.Group))fail(spec.id,'root must be THREE.Group');
    const asset=value.root;
    if(asset.parent || asset.position.length()>1e-8 || asset.quaternion.angleTo(new THREE.Quaternion())>1e-8 || asset.scale.distanceTo(new THREE.Vector3(1,1,1))>1e-8)fail(spec.id,'root must have identity transform and no parent');
    if(!value.bindings || !value.sockets || !value.motions)fail(spec.id,'bindings/sockets/motions must be present');
    const descendants=new Set();let meshCount=0,triangles=0;
    asset.traverse(o=>{descendants.add(o);if(o.isLight || o.isCamera)fail(spec.id,'module cannot add lights or cameras');if(o.isMesh){meshCount++;const p=o.geometry?.attributes?.position;if(!p)fail(spec.id,'mesh missing vertices');triangles+=(o.geometry.index?.count??p.count)/3;for(let i=0;i<p.array.length;i++)if(!Number.isFinite(p.array[i]))fail(spec.id,'nonfinite geometry');}});
    if(meshCount===0 || meshCount>1800 || triangles>250000)fail(spec.id,'geometry budget exceeded or empty');
    asset.updateMatrixWorld(true);
    const bounds=new THREE.Box3().setFromObject(asset);
    const [w,h,d]=spec.size;
    if(bounds.isEmpty() || bounds.min.x < -w/2-tolerance || bounds.max.x > w/2+tolerance || bounds.min.y < -tolerance || bounds.max.y > h+tolerance || bounds.min.z < -d/2-tolerance || bounds.max.z > d/2+tolerance)fail(spec.id,'local bounds exceed confirmed size: '+JSON.stringify({min:bounds.min.toArray(),max:bounds.max.toArray(),limit:spec.size}));
    for(const key of spec.bindings){const object=value.bindings[key];if(!descendants.has(object))fail(spec.id,'binding '+key+' is outside module');if(new THREE.Box3().setFromObject(object).isEmpty())fail(spec.id,'binding '+key+' has no geometry');objects[`${spec.id}.${key}`]=object;}
    if(spec.sockets)for(const [key,expected]of Object.entries(spec.sockets)){const local=value.sockets[key];if(!finiteArray(local,3)||new THREE.Vector3(...local).distanceTo(new THREE.Vector3(...expected))>.025)fail(spec.id,'socket mismatch '+key);}
    for(const [key,local]of Object.entries(value.sockets)){if(!finiteArray(local,3))fail(spec.id,'invalid socket '+key);sockets[`${spec.id}.${key}`]=new THREE.Vector3(...local);}
    for(const [key,motion]of Object.entries(value.motions)){const target=value.bindings[motion.binding];if(!descendants.has(target)||target===asset||!finiteArray(motion.closed,3)||!finiteArray(motion.opened,3))fail(spec.id,'invalid motion '+key);if(target.position.distanceTo(new THREE.Vector3(...motion.closed))>.001)fail(spec.id,'motion closed position differs from initial pose');const delta=new THREE.Vector3(...motion.opened).sub(new THREE.Vector3(...motion.closed));if(delta.length()<.1 || delta.length()>1 || Math.abs(delta.x)>.001 || Math.abs(delta.y)>.001 || delta.z<0)fail(spec.id,'drawer motion must open along local +Z');motions[`${spec.id}.${key}`]={...motion,target};}
    if(spec.module==='cabinet'&&!motions[`${spec.id}.drawer`])fail(spec.id,'cabinet missing drawer motion');
    asset.name=spec.id;asset.position.fromArray(spec.position);asset.rotation.y=spec.rotationY??0;root.add(asset);asset.updateMatrixWorld(true);
    for(const [key,local]of Object.entries(value.sockets))sockets[`${spec.id}.${key}`].applyMatrix4(asset.matrixWorld);
    assets[spec.id]=value;
    reports.push({id:spec.id,module:spec.module,meshes:meshCount,triangles:Math.round(triangles),localBounds:{min:bounds.min.toArray(),max:bounds.max.toArray()}});
  }
  root.updateMatrixWorld(true);
  // Furniture envelopes exclude room, rug and deliberate bed-rope relationships inside one asset.
  const furniture=layout.instances.filter(s=>!['shell','rug'].includes(s.kind));
  const intersections=[];
  for(let i=0;i<furniture.length;i++)for(let j=i+1;j<furniture.length;j++){
    const a=new THREE.Box3().setFromObject(assets[furniture[i].id].root),b=new THREE.Box3().setFromObject(assets[furniture[j].id].root);
    const overlap=a.clone().intersect(b),s=overlap.getSize(new THREE.Vector3());if(!overlap.isEmpty()&&s.x>.025&&s.y>.025&&s.z>.025)intersections.push([furniture[i].id,furniture[j].id]);
  }
  if(intersections.length)throw new Error('assembly furniture intersections: '+JSON.stringify(intersections));
  const rope=sockets['bed.rope_end'],pillow=sockets['bed.pillow'];
  if(layout.instances.some(s=>s.module==='bed') && (!rope || !pillow || rope.distanceTo(pillow)>.2))throw new Error('rope end must be close to pillow');
  const portal=objects['room.vent'] ? new THREE.Box3().setFromObject(objects['room.vent']) : null;
  const actual=portal?.getCenter(new THREE.Vector3()),expected=layout.instances.find(s=>s.module==='shell')?.openings.vent.center;
  if(actual && actual.distanceTo(new THREE.Vector3(...expected))>.12)throw new Error('vent binding geometry is not at confirmed opening');
  const openings=[];
  if(objects['room.vent']){
    const room=assets.room.root;
    const allMeshes=[];room.traverse(o=>{if(o.isMesh)allMeshes.push(o);});
    const isIn=(o,parent)=>{while(o){if(o===parent)return true;o=o.parent;}return false;};
    const defs=layout.instances.find(s=>s.module==='shell').openings;
    for(const [kind,opening]of Object.entries(defs)){
      const center=new THREE.Vector3(...opening.center);
      const direction=new THREE.Vector3(kind==='window'?0:kind==='door'?1:-1,0,kind==='window'?-1:0);
      const ignore=kind==='vent'?[]:[objects['room.'+kind],objects['room.shutter']];
      const candidates=allMeshes.filter(o=>!ignore.some(parent=>parent&&isIn(o,parent)));
      let clear=0;
      for(const a of [-.22,0,.22])for(const b of [-.22,0,.22]){
        const sample=center.clone();sample.y+=a*opening.size[1];
        if(kind==='window')sample.x+=b*opening.size[0];else sample.z+=b*opening.size[0];
        const ray=new THREE.Raycaster(sample.clone().addScaledVector(direction,-.22),direction,0,.45);
        if(ray.intersectObjects(candidates,false).length===0)clear++;
      }
      if(clear<2)throw new Error('room: '+kind+' opening appears blocked by structural geometry ('+clear+'/9 samples clear)');
      openings.push({kind,clearSamples:clear,totalSamples:9});
    }
  }
  const checker={openings,assets:reports,intersections,ropeToPillow:rope?.distanceTo(pillow),ventCenter:actual?.toArray(),limitations:['Bounding boxes do not establish navigable paths, true occlusion, original-text truth or semantic completeness.']};
  return{root,objects,sockets,motions,assets,checker};
}
