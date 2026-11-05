// Explicit inspection mode: display failed candidates unchanged, never mark them accepted.
// The strict assemble() and its rejection rules are preserved in assemble.js.
export function inspectOutput(THREE,layout,builders,kit) {
  const root=new THREE.Group(),objects={},motions={},assets={},errors=[];
  for(const spec of layout.instances){
    try{
      const input=structuredClone(spec);delete input.position;delete input.rotationY;
      const value=builders[spec.module](THREE,input,kit);
      if(!(value.root instanceof THREE.Group))throw new Error('builder did not return a Group');
      value.root.position.fromArray(spec.position);value.root.rotation.y=spec.rotationY??0;
      value.root.name=spec.id;root.add(value.root);assets[spec.id]=value;
      for(const [key,object]of Object.entries(value.bindings??{}))objects[spec.id+'.'+key]=object;
      for(const [key,motion]of Object.entries(value.motions??{}))motions[spec.id+'.'+key]={...motion,target:value.bindings[motion.binding]};
    }catch(error){errors.push({id:spec.id,error:error.message});}
  }
  return{root,objects,motions,assets,checker:{accepted:false,mode:'inspect_unchanged_candidates',errors}};
}
