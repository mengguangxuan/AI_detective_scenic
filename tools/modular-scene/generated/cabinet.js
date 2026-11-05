export function buildAsset(THREE, spec, kit){
  const S=spec.size||[1.2,1.1,0.55], W=S[0],H=S[1],D=S[2];
  const t=0.03, gap=0.01, foot=0.02, handleOut=0.045;
  const root=new THREE.Group();
  const M=kit.materials, wood=M.wood, dw=M.darkWood, br=M.brass;
  const bodyD=D-handleOut;
  const innerW=W-2*t, innerD=bodyD-t, zBody=-handleOut/2;
  const bodyH=H-foot, topY=H-foot;
  kit.box(root,[t,bodyH,bodyD],[-(W/2-t/2),bodyH/2+foot,zBody],wood);
  kit.box(root,[t,bodyH,bodyD],[ (W/2-t/2),bodyH/2+foot,zBody],wood);
  kit.box(root,[W-2*t,bodyH,t],[0,bodyH/2+foot,zBody-(bodyD/2-t/2)],dw);
  kit.box(root,[W,t,bodyD],[0,topY+t/2,zBody],wood);
  kit.box(root,[W-2*t,0.02,bodyD-t],[0,foot+0.01,zBody-t/2],dw);
  const fx=W/2-0.06, fz=bodyD/2-0.06+zBody;
  [[-fx,fz],[fx,fz],[-fx,zBody-bodyD/2+0.06],[fx,zBody-bodyD/2+0.06]].forEach(p=>
    kit.cylinder(root,[0.028,0.032,foot],[p[0],foot/2,p[1]],dw));
  const bandH=(bodyH-0.04)/3, midY0=foot+0.02;
  for(let i=1;i<3;i++){
    const y1=bodyH-0.02-i*(bandH+gap), y0=y1-bandH-0.0, cy=(y0+y1)/2;
    const g=new THREE.Group(); g.position.set(0,0,0); root.add(g);
    const fd=0.025, fz2=zBody+bodyD/2-fd/2;
    kit.box(g,[innerW-0.02,bandH,fd],[0,cy,fz2],wood);
    const hy=cy;
    kit.box(g,[0.14,0.022,0.02],[0,hy,zBody+bodyD/2+0.012],dw);
    kit.box(g,[0.10,0.016,0.03],[-W*0.18,hy,zBody+bodyD/2+0.026],br);
    kit.box(g,[0.10,0.016,0.03],[ W*0.18,hy,zBody+bodyD/2+0.026],br);
  }
  const ty1=bodyH-0.02, ty0=ty1-bandH, tcy=(ty0+ty1)/2;
  const drawer=new THREE.Group(); drawer.position.set(0,0,0); root.add(drawer);
  const fd=0.025, fzT=zBody+bodyD/2-fd/2;
  kit.box(drawer,[innerW-0.02,bandH,fd],[0,tcy,fzT],wood);
  kit.box(drawer,[0.14,0.022,0.02],[0,tcy,zBody+bodyD/2+0.012],dw);
  kit.box(drawer,[0.10,0.016,0.03],[-W*0.18,tcy,zBody+bodyD/2+0.026],br);
  kit.box(drawer,[0.10,0.016,0.03],[ W*0.18,tcy,zBody+bodyD/2+0.026],br);
  const bt=0.016, bxW=innerW-0.06, bxD=bodyD-fd-t-0.02;
  const bz=fzT-fd/2-bxD/2;
  kit.box(drawer,[bxW,bt,bxD],[0,tcy-bandH/2+bt/2+0.004,bz],dw);
  const wallH=bandH-0.03, wy=tcy-bandH/2+0.004+bt+wallH/2;
  kit.box(drawer,[bt,wallH,bxD],[-(bxW/2-bt/2),wy,bz],wood);
  kit.box(drawer,[bt,wallH,bxD],[ (bxW/2-bt/2),wy,bz],wood);
  kit.box(drawer,[bxW,wallH,bt],[0,wy,bz-bxD/2+bt/2],wood);
  return {root,
    bindings:{chest:root,drawer:drawer},
    sockets:{},
    motions:{drawer:{binding:'drawer',closed:[0,0,0],opened:[0,0,0.42]}} };
}
