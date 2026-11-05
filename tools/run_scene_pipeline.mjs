// Generic local orchestration. Scene plan, module code and assembler code are all model-generated.
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import ts from '../frontend/node_modules/typescript/lib/typescript.js';
import {chromium} from '../frontend/node_modules/playwright/index.mjs';
import {principles,contract,planner,moduleTask,assemblerTask,reviewer} from './scene-pipeline/prompts.mjs';
import {limits,resumeState} from './scene-pipeline/policy.mjs';
import {modelFailureDetails,modelFailureMessage} from './scene-pipeline/model-failure.mjs';
import {planIssues} from './scene-pipeline/contract.js';
import {savedAnswer} from './scene-pipeline/receipts.mjs';
process.stdout.on('error',()=>{});process.stderr.on('error',()=>{});
const root=path.resolve(fileURLToPath(new URL('../',import.meta.url)));
const sceneTypes=fs.readFileSync(path.join(root,'tools/scene-pipeline/types.ts'),'utf8');
const home=path.join(root,'backend/.build-cache/scene-pipeline');fs.mkdirSync(home,{recursive:true});
const authorInspection=process.argv.includes('--inspect');
const productionArg=process.argv.indexOf('--production'),productionMode=productionArg>=0,viewOnly=process.argv.includes('--view-only');
const privateToken=crypto.randomBytes(32).toString('hex');
let address='http://127.0.0.1:5190';const reviewToken=crypto.randomBytes(24).toString('hex');
const write=(file,value)=>{
 const temp=file+'.'+crypto.randomUUID()+'.tmp';fs.writeFileSync(temp,JSON.stringify(value,null,2));
 try{for(let attempt=0;;attempt++){try{fs.renameSync(temp,file);break;}catch(error){if(!['EPERM','EBUSY','EACCES'].includes(error.code)||attempt>=39)throw error;Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,25);}}}
 finally{if(fs.existsSync(temp))fs.unlinkSync(temp);}
};
const read=file=>JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
const arg=process.argv.indexOf('--input');let resume=process.argv.includes('--resume');let job,folder,input;
if(productionMode){
 folder=path.resolve(process.argv[productionArg+1]);
 const allowed=[path.join(root,'backend/.build-cache/scene-production'),path.join(root,'backend/target/scene-artifacts')];
 if(!allowed.some(base=>path.dirname(folder)===base)||!/^[0-9a-f-]{36}$/.test(path.basename(folder)))throw new Error('Invalid production directory');
 if(viewOnly||resume){job=read(path.join(folder,'status.json'));input=read(path.join(folder,'input.json'));}
 else{
  if(fs.existsSync(path.join(folder,'status.json')))throw new Error('Existing production must not be regenerated');
  const author=read(path.join(folder,'author-input.json'));
  input={manifest:{inputOrigin:'user_form',title:author.title,targetPlaceId:author.targetPlaceId},sourceFields:author.fields};
  fs.mkdirSync(path.join(folder,'requests'),{recursive:true});fs.mkdirSync(path.join(folder,'modules'),{recursive:true});
  job={id:path.basename(folder),stage:'created',stageLabel:'任务已创建',message:'作者原始字段已保存',firstCallSucceeded:false,calls:0,tokens:0,events:[],issues:[],round:0,startedAt:new Date().toISOString()};
  write(path.join(folder,'input.json'),input);
 }
}else if(arg>=0){
 const requested=path.resolve(root,process.argv[arg+1]);const manifest=read(requested);
 const sourcePath=path.resolve(root,manifest.sourceDocument);if(!sourcePath.startsWith(root+path.sep))throw new Error('Input source must be inside project');
 const source=read(sourcePath);const selected={};
 for(const key of manifest.fieldSelection){if(!Object.hasOwn(source,key))throw new Error('Input field missing: '+key);selected[key]=source[key];}
 // Mechanical field projection only; values are copied without rewriting their text.
 if(Array.isArray(selected.entities))selected.entities=selected.entities.filter(e=>e.initialPlace===manifest.targetPlaceId);
 if(Array.isArray(selected.sceneRequirements))selected.sceneRequirements=selected.sceneRequirements.filter(r=>r.places?.includes(manifest.targetPlaceId));
 input={manifest,sourceFields:selected};const id=crypto.randomUUID();folder=path.join(home,id);fs.mkdirSync(folder);fs.mkdirSync(path.join(folder,'requests'));fs.mkdirSync(path.join(folder,'modules'));
 job={id,stage:'created',stageLabel:'任务已创建',message:'原始字段已保存，等待首次调用',firstCallSucceeded:false,calls:0,tokens:0,events:[],issues:[],round:0,inputPath:path.relative(root,requested),startedAt:new Date().toISOString()};write(path.join(folder,'input.json'),input);write(path.join(home,'current.json'),{id});
}else{const current=read(path.join(home,'current.json'));folder=path.join(home,current.id);job=read(path.join(folder,'status.json'));input=read(path.join(folder,'input.json'));}
function update(stage,label,message){job.stage=stage;job.stageLabel=label;job.message=message;job.events.push({time:new Date().toISOString(),message});job.events=job.events.slice(-35);write(path.join(folder,'status.json'),job);console.log(label);}
function checkpoint(){write(path.join(folder,'status.json'),job);}
job.progress??={generationCalls:0,repairAttempts:0,runtimeCheckAttempts:0,visualReviewAttempts:0,overallReviewAttempts:0};
job.limits=limits;
checkpoint();
let transport=productionMode?read(path.join(folder,'transport.json')):null;
if(transport&&(!Number.isInteger(transport.port)||transport.port<1||transport.port>65535||!/^[0-9a-f]{64}$/.test(transport.token)))throw new Error('Invalid model transport');
const config=productionMode?{baseUrl:'https://tokenhub.tencentmaas.com/v1',model:'configured',apiKey:null}:read(path.join(root,'backend/config/model-api.local.json'));
if(config.baseUrl.replace(/\/$/,'')!=='https://tokenhub.tencentmaas.com/v1')throw new Error('Expected configured TokenHub endpoint');
const key=productionMode?null:process.env.TOKENHUB_API_KEY?.trim()||config.apiKey;
async function ask(role,system,payload,maxTokens=4500){
 if(job.calls>=limits.maxCalls)throw new Error('Configured model call limit exhausted');
 const number=++job.calls;if(role==='visual-review')job.progress.visualReviewAttempts++;else if(role==='overall-review')job.progress.overallReviewAttempts++;else if(!role.startsWith('repair-')&&role!=='failure-analysis')job.progress.generationCalls++;checkpoint();const request={model:config.model,messages:[{role:'system',content:system+(role.endsWith('review')?'':'\n共享通用类型（模型代码为JavaScript，返回形状必须符合类型）：\n'+sceneTypes)},{role:'user',content:Array.isArray(payload)?payload:typeof payload==='string'?payload:JSON.stringify(payload)}],max_tokens:maxTokens,stream:false,thinking:{type:'disabled'},response_format:{type:'json_object'}};
 const prefix=path.join(folder,'requests',String(number).padStart(2,'0')+'-'+role);write(prefix+'.request.json',request);
 if(productionMode){
  transport=read(path.join(folder,'transport.json'));
  if(!Number.isInteger(transport.port)||transport.port<1||transport.port>65535||!/^[0-9a-f]{64}$/.test(transport.token))throw new Error('Invalid model transport');
  const userMessage=request.messages[1].content;
  const modelInput={system:request.messages[0].content,user:Array.isArray(userMessage)?userMessage.filter(p=>p.type==='text').map(p=>p.text).join('\n'):userMessage,images:Array.isArray(userMessage)?userMessage.filter(p=>p.type==='image_url').map(p=>{const match=/^data:(image\/(?:png|jpeg));base64,(.+)$/.exec(p.image_url.url);if(!match)throw new Error('Invalid screenshot input');return{mediaType:match[1],base64Data:match[2]};}):[]};
  let response;try{response=await fetch('http://127.0.0.1:'+transport.port+'/complete',{method:'POST',redirect:'error',headers:{'X-Scene-Transport':transport.token,'Content-Type':'application/json'},body:JSON.stringify({input:modelInput,maxTokens}),signal:AbortSignal.timeout(190000)});}catch{throw new Error(role+': model service unavailable; no transport retry');}
  if(!response.ok){
   let body;try{body=await response.json();}catch{}
   const failure=modelFailureDetails(body?.error);
   write(prefix+'.failure.json',failure??{code:'ModelServiceUnavailable',bridgeStatus:response.status});
   if(failure){job.tokens+=failure.totalTokens;checkpoint();}
   throw new Error(role+': '+modelFailureMessage(failure));
  }
  const result=await response.json();if(typeof result.content!=='string'||result.content.length>90000)throw new Error(role+': invalid model result');
  write(prefix+'.response.json',result);job.tokens+=result.totalTokens??0;checkpoint();return JSON.parse(result.content);
 }

 let response;try{response=await fetch(config.baseUrl.replace(/\/$/,'')+'/chat/completions',{method:'POST',redirect:'error',headers:{Authorization:'Bearer '+key,'Content-Type':'application/json'},body:JSON.stringify(request),signal:AbortSignal.timeout(180000)});}catch{throw new Error(role+': provider connection failed; no transport retry');}
 if(!response.ok)throw new Error(role+': provider HTTP '+response.status);
 const raw=await response.text();if(raw.length>4*1024*1024)throw new Error(role+': response too large');const result=JSON.parse(raw);write(prefix+'.response.json',result);job.tokens+=result.usage?.total_tokens??0;checkpoint();
 const choice=result.choices?.[0];if(choice?.finish_reason!=='stop')throw new Error(role+': incomplete model output');const value=JSON.parse(choice.message.content);return value;
}
const safeId=id=>typeof id==='string'&&/^[a-z][a-z0-9_]{0,45}$/.test(id);
function validateCode(code,filename,exportName){
 if(typeof code!=='string'||code.length>90000)throw new Error(filename+': missing/oversized code');fs.writeFileSync(filename,code);
 const syntax=spawnSync(process.execPath,['--check',filename],{encoding:'utf8'});if(syntax.status!==0)throw new Error(path.basename(filename)+': syntax error '+syntax.stderr.slice(0,600));
 const tree=ts.createSourceFile(filename,code,ts.ScriptTarget.Latest,true,ts.ScriptKind.JS);
 const forbidden=new Set(['window','document','globalThis','self','fetch','XMLHttpRequest','WebSocket','Worker','eval','Function','localStorage','sessionStorage','navigator','location','setTimeout','setInterval','requestAnimationFrame','constructor','__proto__','prototype','Reflect','Proxy','process','require']);
 function visit(n){if(ts.isImportDeclaration(n)||ts.isImportEqualsDeclaration(n)||ts.isWhileStatement(n)||ts.isDoStatement(n))throw new Error(path.basename(filename)+': unsupported construct');if(ts.isIdentifier(n)&&forbidden.has(n.text)){const field=(ts.isPropertyAssignment(n.parent)&&n.parent.name===n)||(ts.isPropertyAccessExpression(n.parent)&&n.parent.name===n&&!['constructor','prototype','__proto__'].includes(n.text));if(!field)throw new Error(path.basename(filename)+': forbidden access '+n.text);}if(ts.isCallExpression(n)&&n.expression.kind===ts.SyntaxKind.ImportKeyword)throw new Error(path.basename(filename)+': dynamic import');ts.forEachChild(n,visit);}visit(tree);
 if(!code.includes('export function '+exportName))throw new Error(path.basename(filename)+': missing export '+exportName);
}
async function capture(){
 const browser=await chromium.launch({headless:true,executablePath:process.env.SCENE_BROWSER_EXECUTABLE||undefined,args:['--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 try{
  const context=await browser.newContext({viewport:{width:1200,height:800},extraHTTPHeaders:{'X-Scene-Review':reviewToken}});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text().slice(0,400));});
  await page.goto(address+'/preview',{waitUntil:'networkidle',timeout:60000});await page.waitForFunction(()=>window.pipelineReady||window.pipelineError,null,{timeout:60000});
  const failed=await page.evaluate(()=>window.pipelineError);if(failed){const error=new Error(failed);error.issues=await page.evaluate(()=>window.pipelineIssues);throw error;}const report=await page.evaluate(()=>window.pipelineScene.report);if(errors.length)throw new Error(errors.join(';'));
  const count=await page.evaluate(()=>window.pipelineScene.views.length);const images=[];
  for(let i=0;i<Math.min(count,6);i++){await page.evaluate(i=>window.pipelineScene.showView(i),i);await page.waitForTimeout(300);const filename=path.join(folder,'view-'+i+'.png');await page.screenshot({path:filename,timeout:60000});images.push(filename);}
  const views=await page.evaluate(()=>window.pipelineScene.views);write(path.join(folder,'program-checks.json'),report);return{report,images,views};
 }finally{await browser.close();}
}
async function review(role,captured,plan){
 const text={rawInput:input,plan,programChecks:captured.report,screenshotViews:captured.views};const content=[{type:'text',text:JSON.stringify(text)},...captured.images.map(file=>({type:'image_url',image_url:{url:'data:image/png;base64,'+fs.readFileSync(file).toString('base64')}}))];
 // Deliberately no generation messages, prior reviews or model self-evaluations in either context.
 const result=await ask(role,reviewer+(role==='overall-review'?' 本次额外独立审查整间房的整体合理性，避免只看单个部件。':''),content,3500);
 if(typeof result.passed!=='boolean'||!Array.isArray(result.issues)||!Array.isArray(result.uncertain)||result.issues.some(i=>!['blocking','minor'].includes(i.severity)||typeof i.evidence!=='string'||!Array.isArray(i.files)))throw new Error('Invalid reviewer result');
 if(result.passed&&(result.uncertain.length||result.issues.some(i=>i.severity==='blocking')))result.passed=false;
 return result;
}
function allFiles(plan){return Object.fromEntries(['assembler',...plan.modules.map(m=>m.id)].map(id=>{const file=id==='assembler'?path.join(folder,'assembler.js'):path.join(folder,'modules',id+'.js');return[id,fs.existsSync(file)?fs.readFileSync(file,'utf8'):''];}));}
async function repairProblems(plan,problems){
   let pending=job.pendingRepair;
   if(!pending){
    job.progress.repairAttempts++;
    const signature=JSON.stringify(problems.map(p=>[p.code??p.error,p.target?.nodeId,p.target?.motionId]));
    let diagnosis;
    if(signature===job.lastFailureSignature){
      update('diagnosing','DeepSeek正在诊断重复失败','向独立诊断请求提供完整相关模块与组装器，避免重复短错误');
      diagnosis=await ask('failure-analysis',principles+'\n'+contract+'\n找出重复错误的根因和应修文件。返回{diagnosis:string,targets:模块id或assembler的数组}。不要写代码，不要只复述错误。',{rawInput:input,plan,problems,files:allFiles(plan)},3000);
    }
    job.lastFailureSignature=signature;
    let targets=new Set();
    for(const issue of problems){if(issue.file)targets.add(issue.file);for(const file of issue.files??[])targets.add(file);}
    targets=new Set([...targets].filter(id=>id==='assembler'||plan.modules.some(m=>m.id===id)));
    for(const id of diagnosis?.targets??[])if(id==='assembler'||plan.modules.some(m=>m.id===id))targets.add(id);
    if(!targets.size)targets.add('assembler');
    pending={targets:[...targets],problems,diagnosis,errors:[]};job.pendingRepair=pending;checkpoint();
   }
   job.issues=pending.problems;
   update('repairing','DeepSeek正在按反馈修正','从现有文件与待修队列继续；累计token用量仅记录，不设保护上限');
   for(const id of [...pending.targets]){
    const filename=id==='assembler'?path.join(folder,'assembler.js'):path.join(folder,'modules',id+'.js');
    const previous=fs.existsSync(filename)?fs.readFileSync(filename,'utf8'):'';
    const result=await ask('repair-'+id,principles+'\n'+contract+'\n'+(id==='assembler'?assemblerTask:moduleTask)+'\n修正反馈指定的问题，返回完整替换文件，保持其它模块接口。',{rawInput:input,plan,target:id,problems:pending.problems,previous,diagnosis:pending.diagnosis,relatedFiles:allFiles(plan)},id==='assembler'?5500:4500);
    try{validateCode(result.code,filename,id==='assembler'?'assembleScene':'buildAsset');}catch(error){pending.errors.push({file:id,error:error.message});}
    pending.targets.shift();checkpoint();
   }
   const errors=pending.errors;delete job.pendingRepair;checkpoint();return errors;
}

async function run(){
 try{
  if(resume&&job.stage==='passed')return;
  update('planning','DeepSeek正在审核字段和规划','首次请求：直接读取原始字段及来源摘录，不使用助手转述的场景描述');
  let plan=resume&&job.firstCallSucceeded&&fs.existsSync(path.join(folder,'plan.json'))?read(path.join(folder,'plan.json')):await ask('planning',principles+'\n'+planner,{rawInput:input},5000);
  if(typeof plan.inputSufficient!=='boolean'||!Array.isArray(plan.gaps))throw new Error('Planner output missing input sufficiency');
  job.firstCallSucceeded=true;job.firstCallAt??=new Date().toISOString();write(path.join(folder,'plan.json'),plan);checkpoint();
  if(!plan.inputSufficient){job.issues=plan.gaps;update('needs_input','需要补充原始字段','DeepSeek认为字段不足，任务已停在输入阶段，未补造事实');return;}
  const validPlan=p=>planIssues(p).length===0;
  if(productionMode&&resume&&fs.existsSync(path.join(folder,'layout-approved.json'))&&!validPlan(plan))throw new Error('Saved approved layout invalid; refusing to replace it');
  for(let attempt=0;!validPlan(plan)&&attempt<2;attempt++){
    update('planning','DeepSeek正在补齐生成协议','规划结果缺少完整模块或实例，自动反馈给模型补齐');
    plan=await ask('planning-protocol-repair',principles+'\n'+planner+'\n模块和实例列表不得为空；inputSufficient=true时必须给出2至12个完整模块及实际实例，每个模块必须有id、task、specs。此请求要求实际可执行的分工计划，不是空结构示例。',{rawInput:input,invalidPlan:plan,protocolErrors:planIssues(plan)},6000);
    write(path.join(folder,'plan.json'),plan);
  }
  if(!validPlan(plan))throw new Error('Invalid module plan after finite protocol repair');
  if(productionMode&&!fs.existsSync(path.join(folder,'layout-approved.json'))){job.issues=[];update('awaiting_layout','请先确认模型布局','布局方案已生成，确认后才开始编写部件和组装器');return;}
  const artifactsPresent=fs.existsSync(path.join(folder,'assembler.js'))&&plan.modules.every(m=>fs.existsSync(path.join(folder,'modules',m.id+'.js')));
  const continuation=resume?resumeState(job,artifactsPresent):{run:true,startRound:0,issues:[]};
  if(!continuation.run){update('needs_attention','自动检查尚未通过','已达到'+limits.maxRounds+'轮上限，保留现有进度');return;}
  const generationErrors=[];
  if(continuation.startRound===0){
  update('generating','DeepSeek正在生成独立模块','首次调用已成功；布局与部件方案均来自DeepSeek，后台继续运行');

  for(const module of plan.modules){
   const payload={rawInput:input,plan,module};
   const cached=resume?savedAnswer(folder,payload):null;
   const result=cached?.answer??await ask('module-'+module.id,principles+'\n'+contract+'\n'+moduleTask,payload,4500);
   if(cached){job.reusedAnswers??={};job.reusedAnswers[module.id]=cached.source;checkpoint();}
   try{validateCode(result.code,path.join(folder,'modules',module.id+'.js'),'buildAsset');}catch(error){generationErrors.push({file:module.id,error:error.message});}
   update('generating','DeepSeek正在生成独立模块','模块已返回：'+module.id);
  }
  update('assembling','DeepSeek正在编写组装器','模型自行调用各模块并负责相互位置、灯光与预览视角');
  const assemblerPayload={rawInput:input,plan,modules:plan.modules.map(m=>({id:m.id,code:fs.readFileSync(path.join(folder,'modules',m.id+'.js'),'utf8')}))};
  const cachedAssembler=resume?savedAnswer(folder,assemblerPayload):null;
  const assembled=cachedAssembler?.answer??await ask('assembler',principles+'\n'+contract+'\n'+assemblerTask,assemblerPayload,5500);
  try{validateCode(assembled.code,path.join(folder,'assembler.js'),'assembleScene');}catch(error){generationErrors.push({file:'assembler',error:error.message});}

  }
  let problems=continuation.startRound>0?continuation.issues:generationErrors;
  job.issues=problems;checkpoint();
  if(continuation.startRound>0&&problems.length)problems=await repairProblems(plan,problems);
  for(let round=continuation.startRound;round<limits.maxRounds;round++){
   job.round=round+1;checkpoint();let captured;
   if(!problems.length){
    update('capturing','正在自动渲染并截图','使用模型组装器提供的视角拍摄实际3D画面');
    job.progress.runtimeCheckAttempts++;checkpoint();
    try{captured=await capture();}catch(error){problems=error.issues?.length?error.issues:[{file:'assembler',stage:'runtime',code:'runtime_failure',error:'Actual render: '+error.message}];job.issues=problems;checkpoint();}
   }
   if(captured){
    update('visual_review','DeepSeek正在进行视觉检查','新请求只包含原始字段、模型布局、程序检查及实际截图');
    const visual=await review('visual-review',captured,plan);write(path.join(folder,'visual-review-'+round+'.json'),visual);job.reviews={...job.reviews,visual};job.firstVisualReviewCompletedAt??=new Date().toISOString();job.firstVisualReview??=visual;checkpoint();
    update('overall_review','DeepSeek正在独立复核整体','另起无生成历史、无上次审核结论的新上下文检查整间房');
    const overall=await review('overall-review',captured,plan);write(path.join(folder,'overall-review-'+round+'.json'),overall);
    job.reviews={visual,overall};job.issues=[...visual.issues,...overall.issues,...visual.uncertain.map(e=>({severity:'blocking',evidence:e})),...overall.uncertain.map(e=>({severity:'blocking',evidence:e}))];checkpoint();
    if(visual.passed&&overall.passed){update('passed','视觉与整体审核均已通过','模型验收通过，场景预览入口已开放；尚未发布到正式案件');return;}
    problems=job.issues;
   }
   if(round===limits.maxRounds-1){job.issues=problems;update('needs_attention','自动检查尚未通过','已达到'+limits.maxRounds+'轮自动检查上限；保留草稿和问题，预览未开放');return;}
   problems=await repairProblems(plan,problems);
  }
 }catch(error){job.lastError=String(error.message).slice(0,1200);if(!job.pendingRepair)job.issues=[{error:job.lastError}];update('failed','任务已停止',String(error.message).slice(0,250));}
}
const tooling=path.join(root,'tools/scene-pipeline'),three=path.join(root,'frontend/node_modules/three');
const staticFiles=new Map([
 ['/',[path.join(tooling,'status.html'),'text/html; charset=utf-8',false]],
 ['/inspect',[path.join(tooling,'preview.html'),'text/html; charset=utf-8',true]],
 ['/preview',[path.join(tooling,'preview.html'),'text/html; charset=utf-8',true]],
 ['/contract.js',[path.join(tooling,'contract.js'),'text/javascript',true]],
 ['/runtime.js',[path.join(tooling,'runtime.js'),'text/javascript',true]],
 ['/kit.js',[path.join(root,'tools/modular-scene/kit.js'),'text/javascript',true]],
 ['/plan.json',[path.join(folder,'plan.json'),'application/json',true]],
 ['/assembler.js',[path.join(folder,'assembler.js'),'text/javascript',true]],
 ['/three.module.js',[path.join(three,'build/three.module.js'),'text/javascript',true]],
 ['/three.core.js',[path.join(three,'build/three.core.js'),'text/javascript',true]],
 ['/addons/controls/OrbitControls.js',[path.join(three,'examples/jsm/controls/OrbitControls.js'),'text/javascript',true]],
]);
const server=http.createServer((req,res)=>{
 if(productionMode&&req.headers.host!==new URL(address).host){res.writeHead(403).end();return;}
 let pathname;try{pathname=new URL(req.url,address).pathname;}catch{res.writeHead(400).end();return;}
 if(productionMode&&pathname==='/control/confirm-layout'&&req.method==='POST'){
  if(req.headers['x-scene-control']!==privateToken){res.writeHead(403).end();return;}
  if(fs.existsSync(path.join(folder,'layout-approved.json'))){res.writeHead(200).end();return;}
  if(job.stage!=='awaiting_layout'){res.writeHead(409).end();return;}
  write(path.join(folder,'layout-approved.json'),{confirmedAt:new Date().toISOString()});
  resume=true;update('planning','布局已确认','继续按已确认的模型布局制作');res.writeHead(202).end();void run();return;
 }
 if(!['GET','HEAD'].includes(req.method)){res.writeHead(405).end();return;}
 const reviewAccess=req.headers['x-scene-review']===reviewToken;
 const cookieName='scene_preview_'+job.id.replaceAll('-','');
 const cookieAccess=String(req.headers.cookie??'').split(';').some(value=>value.trim()===cookieName+'='+privateToken);
 const accepted=()=>job.stage==='passed'&&job.reviews?.visual?.passed===true&&job.reviews?.overall?.passed===true&&fs.existsSync(path.join(folder,'program-checks.json'))&&read(path.join(folder,'program-checks.json')).passed===true;
 if(productionMode&&pathname==='/author-preview'){
  const supplied=new URL(req.url,address).searchParams.get('token');
  if(!accepted()||supplied!==privateToken){res.writeHead(403).end();return;}
  res.writeHead(302,{'Location':'/preview','Cache-Control':'no-store','Set-Cookie':cookieName+'='+privateToken+'; HttpOnly; SameSite=Strict; Path=/; Max-Age=3600'}).end();return;
 }
 if(productionMode&&!reviewAccess&&!cookieAccess){res.writeHead(403,{'Cache-Control':'no-store'}).end();return;}
 if(pathname==='/favicon.ico'){res.writeHead(204).end();return;}
 const headers={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'"};
 if(pathname==='/api/status'||pathname==='/api/input'){res.writeHead(200,{...headers,'Content-Type':'application/json; charset=utf-8'}).end(JSON.stringify(pathname==='/api/status'?{...job,authorInspection}:input));return;}
 let entry=staticFiles.get(pathname);
 const modulePath=/^\/modules\/([a-z][a-z0-9_]{0,45})\.js$/.exec(pathname);
 if(modulePath)entry=[path.join(folder,'modules',modulePath[1]+'.js'),'text/javascript',true];
 if(!entry){res.writeHead(404).end();return;}
 if(productionMode&&entry?.[2]&&!reviewAccess&&!accepted()){res.writeHead(403).end();return;}
 if(entry[2]&&job.stage!=='passed'&&req.headers['x-scene-review']!==reviewToken&&!(authorInspection&&pathname!=='/preview')){res.writeHead(403,{...headers,'Content-Type':'text/plain; charset=utf-8'}).end('场景尚未通过模型验收，请查看状态接口。');return;}
 if(!fs.existsSync(entry[0])){res.writeHead(404).end();return;}
 res.writeHead(200,{...headers,'Content-Type':entry[1]});if(req.method==='HEAD'){res.end();return;}const stream=fs.createReadStream(entry[0]);stream.on('error',()=>res.destroy());stream.pipe(res);
});
server.on('error',()=>{console.error('Status server could not start. No generation started.');process.exit(1);});
server.listen(productionMode?0:5190,'127.0.0.1',()=>{address='http://127.0.0.1:'+server.address().port;
 if(productionMode)write(path.join(folder,'access.json'),{port:server.address().port,pid:process.pid,token:privateToken});
 console.log('Scene workflow server ready');if(productionMode?!viewOnly:arg>=0||resume)void run();});
