import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
test('private production viewer requires capability; unaccepted candidates cannot be viewed or confirmed',async()=>{
 const folder=path.join(root,'backend/.build-cache/scene-production',randomUUID());
 fs.mkdirSync(folder,{recursive:true});
 const json=(name,value)=>fs.writeFileSync(path.join(folder,name),JSON.stringify(value));
 json('input.json',{sourceFields:{raw:'test source'}});
 json('transport.json',{port:1,token:'a'.repeat(64)}); // view-only must never call a provider.
 json('status.json',{id:path.basename(folder),stage:'failed',calls:0,tokens:0,round:0,events:[]});
 const process=spawn(globalThis.process.execPath,[path.join(root,'tools/run_scene_pipeline.mjs'),'--production',folder,'--view-only'],{stdio:'pipe'});
 let errors='';process.stderr.on('data',data=>errors+=data);
 try{
  for(let n=0;!fs.existsSync(path.join(folder,'access.json'))&&n<100;n++)await pause(100);
  assert(fs.existsSync(path.join(folder,'access.json')),'viewer did not start: '+errors);
  const access=JSON.parse(fs.readFileSync(path.join(folder,'access.json')));
  const address='http://127.0.0.1:'+access.port;
  assert.equal((await fetch(address+'/api/input')).status,403);
  assert.equal((await fetch(address+'/preview')).status,403);
  assert.equal((await fetch(address+'/author-preview?token='+access.token,{redirect:'manual'})).status,403);
  assert.equal((await fetch(address+'/control/confirm-layout',{method:'POST'})).status,403);
  assert.equal((await fetch(address+'/control/confirm-layout',{method:'POST',headers:{'X-Scene-Control':access.token}})).status,409);
  assert.equal(JSON.parse(fs.readFileSync(path.join(folder,'status.json'))).calls,0);
 }finally{process.kill();await new Promise(resolve=>process.once('exit',resolve));fs.rmSync(folder,{recursive:true,force:true});}
});
test('accepted private viewer exchanges its capability for an HttpOnly cookie',async()=>{
 const folder=path.join(root,'backend/.build-cache/scene-production',randomUUID());fs.mkdirSync(folder,{recursive:true});
 const json=(name,value)=>fs.writeFileSync(path.join(folder,name),JSON.stringify(value));
 json('input.json',{sourceFields:{raw:'test source'}});json('transport.json',{port:1,token:'a'.repeat(64)});
 json('program-checks.json',{passed:true});
 json('status.json',{id:path.basename(folder),stage:'passed',calls:0,tokens:0,round:1,events:[],reviews:{visual:{passed:true},overall:{passed:true}}});
 const process=spawn(globalThis.process.execPath,[path.join(root,'tools/run_scene_pipeline.mjs'),'--production',folder,'--view-only'],{stdio:'pipe'});
 let errors='';process.stderr.on('data',data=>errors+=data);
 try{
  for(let n=0;!fs.existsSync(path.join(folder,'access.json'))&&n<100;n++)await pause(100);
  assert(fs.existsSync(path.join(folder,'access.json')),'viewer did not start: '+errors);
  const access=JSON.parse(fs.readFileSync(path.join(folder,'access.json'))),address='http://127.0.0.1:'+access.port;
  const response=await fetch(address+'/author-preview?token='+access.token,{redirect:'manual'});
  assert.equal(response.status,302);assert.equal(response.headers.get('location'),'/preview');
  const cookie=response.headers.get('set-cookie');assert(cookie.includes('HttpOnly'));assert(cookie.includes('SameSite=Strict'));
  assert.equal((await fetch(address+'/preview',{headers:{Cookie:cookie.split(';')[0]}})).status,200);
  assert.equal((await fetch(address+'/preview')).status,403);
  assert.equal(JSON.parse(fs.readFileSync(path.join(folder,'status.json'))).calls,0);
 }finally{process.kill();await new Promise(resolve=>process.once('exit',resolve));fs.rmSync(folder,{recursive:true,force:true});}
});

test('production sends original form fields to the model service and waits for layout confirmation before generating code',async()=>{
 const {createServer}=await import('node:http');
 const calls=[];
 const spec={parameters:{},sizeBudget:{widthMeters:1,heightMeters:1,depthMeters:1}};
 const placement={positionMeters:{x:0,y:0,z:0},rotation:{xRad:0,yRad:0,zRad:0,order:'XYZ'},scale:{x:1,y:1,z:1}};
 const plan={inputSufficient:true,gaps:[],design:{title:'Synthetic test layout',decisions:[],relationships:[],mood:'test'},modules:[{id:'first',task:'test',specs:[spec]},{id:'second',task:'test',specs:[spec]}],instances:[{id:'one',module:'first',specIndex:0,placement}],assemblerTask:'test'};
 const bridge=createServer(async(req,res)=>{let raw='';for await(const chunk of req)raw+=chunk;calls.push(JSON.parse(raw));if(calls.length===1){res.writeHead(200,{'Content-Type':'application/json'}).end(JSON.stringify({content:JSON.stringify(plan),totalTokens:10}));}else res.writeHead(502,{'Content-Type':'application/json'}).end(JSON.stringify({error:{code:'IncompleteOutput',providerStatus:200,finishReason:'length',maxTokens:4500,totalTokens:17,partialContent:'export function buildAsset('}}));});
 await new Promise(resolve=>bridge.listen(0,'127.0.0.1',resolve));
 const folder=path.join(root,'backend/.build-cache/scene-production',randomUUID());fs.mkdirSync(folder,{recursive:true});
 const json=(name,value)=>fs.writeFileSync(path.join(folder,name),JSON.stringify(value));
 const fields={original:'原始字段内容',custom:{nested:[1,'untouched']}};
 json('author-input.json',{title:'测试提交',targetPlaceId:'place',fields});json('transport.json',{port:bridge.address().port,token:'a'.repeat(64)});
 const process=spawn(globalThis.process.execPath,[path.join(root,'tools/run_scene_pipeline.mjs'),'--production',folder],{stdio:'pipe'});
 let errors='';process.stderr.on('data',data=>errors+=data);
 const status=()=>{try{return JSON.parse(fs.readFileSync(path.join(folder,'status.json')));}catch{return{};}};
 try{
  for(let n=0;status().stage!=='awaiting_layout'&&n<100;n++)await pause(100);
  assert.equal(status().stage,'awaiting_layout',errors);assert.equal(calls.length,1);
  assert.deepEqual(JSON.parse(calls[0].input.user).rawInput.sourceFields,fields);
  assert.equal(status().tokens,10);
  const access=JSON.parse(fs.readFileSync(path.join(folder,'access.json'))),url='http://127.0.0.1:'+access.port+'/control/confirm-layout';
  await pause(200);assert.equal(calls.length,1);
  assert.equal((await fetch(url,{method:'POST',headers:{'X-Scene-Control':access.token}})).status,202);
  for(let n=0;status().stage!=='failed'&&n<100;n++)await pause(100);
  assert.equal(status().stage,'failed');assert.equal(calls.length,2,JSON.stringify(status()));
  assert(status().lastError.includes('finish_reason=length'));assert.equal(status().tokens,27);
  const failure=JSON.parse(fs.readFileSync(path.join(folder,'requests/02-module-first.failure.json')));assert.equal(failure.partialContent,'export function buildAsset(');
  assert(!fs.existsSync(path.join(folder,'modules/first.js')));
  assert.equal((await fetch(url,{method:'POST',headers:{'X-Scene-Control':access.token}})).status,200);
  await pause(200);assert.equal(calls.length,2);
 }finally{process.kill();await new Promise(resolve=>process.once('exit',resolve));await new Promise(resolve=>bridge.close(resolve));fs.rmSync(folder,{recursive:true,force:true});}
});
