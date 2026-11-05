// Main-site persistence check, without starting generation or making provider requests.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {fileURLToPath} from 'node:url';

if(process.platform==='win32')process.env.PLAYWRIGHT_BROWSERS_PATH??='E:\\dev\\playwright-browsers';
const {chromium}=await import('../frontend/node_modules/playwright/index.mjs');
const cache=fileURLToPath(new URL('../frontend/.build-cache/',import.meta.url));
fs.mkdirSync(cache,{recursive:true});
const file=cache+'scene-production-live.json',state=cache+'scene-production-live-browser.json';
const restore=process.argv.includes('--restore');
const browser=await chromium.launch({headless:true});
try{
 const context=await browser.newContext({baseURL:'http://127.0.0.1:5173',...(restore?{storageState:state}:{}),viewport:{width:1440,height:1000}});
 const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:5173/create-scene',{waitUntil:'networkidle'});
 await page.getByRole('heading',{name:'场景制作',exact:true}).waitFor();
 let saved;
 if(!restore){
  const fields={sourceText:'通用保存验收资料，仅检查原始字段保存与恢复，不提交模型。',custom:{nested:['原样保存',17]}};
  await page.getByLabel('制作名称',{exact:true}).fill('场景制作保存验收');
  await page.getByRole('button',{name:'查看原始JSON',exact:true}).click();
  await page.getByLabel('原始JSON字段',{exact:true}).fill(JSON.stringify(fields,null,2));
  await page.getByRole('button',{name:'保存草稿',exact:true}).click();
  await page.getByRole('button',{name:'开始模型制作',exact:true}).waitFor();
  const id=await page.evaluate(()=>localStorage.getItem('detective-scenic:scene-production'));
  assert(id);
  const response=await page.request.post('/api/getsceneproduction',{headers:{'X-Detective-Client':'web'},data:{productionId:id}});
  assert.equal(response.status(),200);saved=await response.json();assert.deepEqual(saved.input.fields,fields);assert.equal(saved.phase,'Draft');assert.equal(saved.status.calls,0);
  fs.writeFileSync(file,JSON.stringify(saved,null,2));await context.storageState({path:state});
 }else saved=JSON.parse(fs.readFileSync(file,'utf8'));
 await page.reload({waitUntil:'networkidle'});
 await page.getByRole('button',{name:saved.input.title,exact:false}).waitFor();
 // The server list is authoritative even if the original tab has no locator after restart.
 const response=await page.request.post('/api/getsceneproduction',{headers:{'X-Detective-Client':'web'},data:{productionId:saved.id}});
 assert.equal(response.status(),200);assert.deepEqual(await response.json(),saved);
 await page.getByLabel('制作名称',{exact:true}).waitFor();
 if(restore)await page.getByRole('button',{name:saved.input.title,exact:false}).click();
 assert.equal(await page.getByLabel('制作名称',{exact:true}).inputValue(),saved.input.title);
 assert.deepEqual(errors,[]);
 await page.screenshot({path:cache+'scene-production-live.png',fullPage:true});
 console.log(restore?'PASS: main-site author identity and original draft survived actual backend restart; no model calls.':'PASS: live main-site author fields save and reload unchanged; no model calls.');
}finally{await browser.close();}
