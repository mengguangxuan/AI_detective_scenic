// Local syntax/global-access screen; this is not a general JavaScript sandbox.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';
import ts from '../frontend/node_modules/typescript/lib/typescript.js';
import * as THREE from '../frontend/node_modules/three/build/three.module.js';
import {assemble} from './modular-scene/assemble.js';
const root=fileURLToPath(new URL('../',import.meta.url)),cache=path.join(root,'frontend/.build-cache/modular-scene');
const layout=JSON.parse(fs.readFileSync(path.join(root,'tools/modular-scene/layout.json'),'utf8'));
const reports=[];
const material=new THREE.MeshStandardMaterial();
const add=(p,g,at,m)=>{if(!m?.isMaterial)throw new Error('kit material must be THREE.Material; received '+typeof m);const o=new THREE.Mesh(g,m);o.position.fromArray(at);p.add(o);return o;};
const kit={materials:Object.fromEntries(['wood','darkWood','plaster','cloth','wicker','rug','metal','brass','glass','stone','soot','mirror'].map(k=>[k,material])),box:(p,s,at,m)=>{if(s.some(v=>!Number.isFinite(v)||v<=0))throw new Error('box dimensions must be positive');return add(p,new THREE.BoxGeometry(...s),at,m);},cylinder:(p,s,at,m,rot=[0,0,0])=>{const o=add(p,new THREE.CylinderGeometry(...s,10),at,m);o.rotation.set(...rot);return o;},tube:(p,ps,r,m)=>add(p,new THREE.TubeGeometry(new THREE.CatmullRomCurve3(ps.map(v=>new THREE.Vector3(...v))),24,r,6,false),[0,0,0],m)};
const builders={};
for(const module of ['shell','bed','cabinet','furnishings']){
 try {
  const file=path.join(cache,module+'.candidate.js'),code=fs.readFileSync(file,'utf8');
  const syntax=spawnSync(process.execPath,['--check',file],{encoding:'utf8'});
  if(syntax.status!==0)throw new Error(syntax.stderr.slice(0,750));
  const source=ts.createSourceFile(file,code,ts.ScriptTarget.Latest,true,ts.ScriptKind.JS);
  const banned=new Set(['window','document','globalThis','self','fetch','XMLHttpRequest','WebSocket','Worker','eval','Function','localStorage','sessionStorage','navigator','location','setTimeout','setInterval','requestAnimationFrame','constructor','__proto__','prototype','Reflect','Proxy','process','require','importScripts']);
  function visit(node){if(ts.isImportDeclaration(node)||ts.isImportEqualsDeclaration(node)||ts.isWhileStatement(node)||ts.isDoStatement(node))throw new Error('unsupported code construct');if(ts.isIdentifier(node)&&banned.has(node.text)){const property=(ts.isPropertyAssignment(node.parent)&&node.parent.name===node)||(ts.isPropertyAccessExpression(node.parent)&&node.parent.name===node&&!['constructor','__proto__','prototype'].includes(node.text));if(!property)throw new Error('forbidden global/property '+node.text);}if(ts.isCallExpression(node)&&node.expression.kind===ts.SyntaxKind.ImportKeyword)throw new Error('dynamic import');ts.forEachChild(node,visit);}
  visit(source);
  if(!code.includes('export function buildAsset'))throw new Error('missing buildAsset export');
  const {buildAsset}=await import(pathToFileURL(file).href);
  // All branches, and every reused instance, are exercised independently.
  const subset={...layout,instances:layout.instances.filter(s=>s.module===module)};
  const checked=assemble(THREE,subset,{[module]:buildAsset},kit);
  builders[module]=buildAsset;
  reports.push({module,passed:true,checks:checked.checker});
 }catch(e){reports.push({module,passed:false,error:e.message});}
}
if(reports.every(r=>r.passed)){
 try{const result=assemble(THREE,layout,builders,kit);reports.push({module:'assembly',passed:true,checks:result.checker});}
 catch(e){reports.push({module:'assembly',passed:false,error:e.message});}
}
fs.writeFileSync(path.join(cache,'preflight.json'),JSON.stringify(reports,null,2));
for(const report of reports)console.log(JSON.stringify(report));
if(reports.some(r=>!r.passed))process.exitCode=1;
else for(const module of Object.keys(builders)){fs.copyFileSync(path.join(cache,module+'.candidate.js'),path.join(cache,module+'.registered.js'));}
