export function makeKit(THREE) {
  let seed=7621;
  const random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};
  function texture(kind){const canvas=document.createElement('canvas');canvas.width=canvas.height=256;const c=canvas.getContext('2d');
    c.fillStyle=kind==='wood'?'#9c734c':kind==='wicker'?'#ba985c':'#eee6cf';c.fillRect(0,0,256,256);
    if(kind==='wood'){for(let i=0;i<700;i++){c.strokeStyle=`rgba(55,30,12,${.03+random()*.12})`;const x=random()*256,y=random()*256;c.beginPath();c.moveTo(x,y);c.lineTo(x+20+random()*70,y+random()*2);c.stroke();}for(let y=0;y<256;y+=64){c.fillStyle='#573e2a';c.fillRect(0,y,256,2);c.fillRect((y*3)%256,y,2,64);}}
    else{for(let i=0;i<256;i+=kind==='wicker'?8:4){c.strokeStyle=kind==='wicker'?'#7f6138':'#aaa08a';c.globalAlpha=.35;c.beginPath();c.moveTo(i,0);c.lineTo(i,256);c.moveTo(0,i);c.lineTo(256,i);c.stroke();}}
    const t=new THREE.CanvasTexture(canvas);t.colorSpace=THREE.SRGBColorSpace;t.wrapS=t.wrapT=THREE.RepeatWrapping;return t;}
  const mat=(color,extra={})=>new THREE.MeshStandardMaterial({color,roughness:.8,...extra});
  const materials={wood:mat(0xffffff,{map:texture('wood')}),darkWood:mat(0x49301f),plaster:mat(0xe5d9bc),cloth:mat(0xffffff,{map:texture('cloth')}),wicker:mat(0xffffff,{map:texture('wicker')}),rug:mat(0x6c2630),metal:mat(0x373a34,{metalness:.6,roughness:.35}),brass:mat(0xb58b42,{metalness:.65,roughness:.35}),glass:mat(0xcce4e9,{transparent:true,opacity:.35}),stone:mat(0x827568),soot:mat(0x171410),mirror:mat(0xc2d6dc,{metalness:.75,roughness:.1})};
  const add=(parent,geometry,position,material)=>{const m=new THREE.Mesh(geometry,material);m.position.fromArray(position);parent.add(m);return m;};
  return {materials,box:(p,s,at,m)=>add(p,new THREE.BoxGeometry(...s),at,m),cylinder:(p,s,at,m,rot=[0,0,0])=>{const o=add(p,new THREE.CylinderGeometry(...s,10),at,m);o.rotation.set(...rot);return o;},tube:(p,points,r,m)=>add(p,new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points.map(v=>new THREE.Vector3(...v))),24,r,6,false),[0,0,0],m)};
}
