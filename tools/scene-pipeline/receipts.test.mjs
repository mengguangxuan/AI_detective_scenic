import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {savedAnswer} from './receipts.mjs';
test('resume reuses a diagnostic answer only for identical raw fields, plan and module payload',()=>{
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'scene-receipts-'));fs.mkdirSync(path.join(folder,'requests'));
 const payload={rawInput:{sourceFields:{sourceText:'raw'}},plan:{title:'approved'},module:{id:'shell'}};
 const write=(name,value)=>fs.writeFileSync(path.join(folder,'requests',name),JSON.stringify(value));
 try{
  write('03-diagnostic.request.json',{messages:[{role:'user',content:JSON.stringify(payload)}],diagnosticOf:'02-module-shell.request.json'});
  write('03-diagnostic.response.json',{content:JSON.stringify({code:'export function buildAsset(){}'})});
  assert.equal(savedAnswer(folder,payload).source,'03-diagnostic.response.json');
  assert.equal(savedAnswer(folder,{...payload,plan:{title:'different'}}),null);
  assert.equal(savedAnswer(folder,{...payload,rawInput:{sourceFields:{sourceText:'changed'}}}),null);
  write('04-module-shell.request.json',{messages:[{role:'user',content:JSON.stringify(payload)}]});
  write('04-module-shell.failure.json',{code:'IncompleteOutput',partialContent:'wrong partial'});
  assert.equal(savedAnswer(folder,payload).source,'03-diagnostic.response.json');
 }finally{fs.rmSync(folder,{recursive:true,force:true});}
});
