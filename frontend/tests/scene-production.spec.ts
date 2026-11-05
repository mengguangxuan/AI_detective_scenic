import {test,expect} from '@playwright/test';

test('scene authoring keeps raw fields, confirms model layout, previews only accepted results and restores saved scenes',async({page})=>{
 let record:any=null;
 let source:any=null;
 let started=0;
 const progress={generationCalls:0,repairAttempts:0,runtimeCheckAttempts:0,visualReviewAttempts:0,overallReviewAttempts:0};
 const layout={title:'验收用模型布局',decisions:['模型作出的布局说明'],items:[{id:'part',label:'验收部件',x:0,z:0,width:1,depth:1,rotationY:0}]};
 await page.route('**/api/**',async route=>{
  const name=new URL(route.request().url()).pathname.split('/').pop();
  const data=route.request().postDataJSON();let reply:any={};
  if(name==='openlocalidentity')reply={mode:'local-demo'};
  else if(name==='listsceneproductions')reply=record?[record]:[];
  else if(name==='savescenedraft'){
   source=data.input;
   record={id:data.productionId,input:data.input,phase:'Draft',createdAt:'2026-11-04T00:00:00Z',savedAt:null,status:{stage:'draft',label:'草稿',message:'已保存',round:0,maxRounds:10,calls:0,tokens:0,progress,issues:[],layout:null},previewUrl:null};reply=record;
  }else if(name==='startsceneproduction'){
   started++;record={...record,phase:'Queued',status:{...record.status,stage:'awaiting_layout',label:'请先确认模型布局',layout}};reply=record;
  }else if(name==='confirmscenelayout'){
   record={...record,status:{...record.status,stage:'passed',label:'视觉与整体审核均已通过',progress:{...progress,runtimeCheckAttempts:1,visualReviewAttempts:1,overallReviewAttempts:1}},previewUrl:'http://127.0.0.1:59999/author-preview?token='+'a'.repeat(64)};reply=record;
  }else if(name==='savesceneartifact'){
   record={...record,phase:'Saved',savedAt:'2026-11-04T00:01:00Z'};reply=record;
  }else if(name==='getsceneproduction')reply=record;
  else throw new Error('Unexpected author-page API: '+name);
  await route.fulfill({json:reply});
 });
 await page.route('http://127.0.0.1:59999/**',route=>route.fulfill({contentType:'text/html; charset=utf-8',body:'<p>验收用私有预览</p>'}));
 await page.goto('/create-scene');
 await expect(page.getByRole('heading',{name:'场景制作',exact:true})).toBeVisible();
 await page.getByLabel('制作名称',{exact:true}).fill('原始字段验收');
 const fields={places:[{id:'sample_room',name:'测试地点'}],custom:{unrecognized:['保持原样',7]},source:{text:'原始填写内容，不能由助手转述'}};
 await page.getByLabel('导入作者字段文件').setInputFiles({name:'author.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(fields))});
 await page.getByLabel('目标场景',{exact:true}).selectOption('sample_room');
 await page.getByRole('button',{name:'保存草稿',exact:true}).click();
 await expect(page.getByRole('button',{name:'开始模型制作',exact:true})).toBeEnabled();
 expect(source.fields).toEqual(fields);expect(started).toBe(0);
 await page.reload();
 await expect(page.getByLabel('制作名称',{exact:true})).toHaveValue('原始字段验收');
 await page.getByRole('button',{name:'开始模型制作',exact:true}).click();
 await expect(page.getByRole('img',{name:'模型生成的场景布局俯视图'})).toBeVisible();
 await expect(page.getByRole('button',{name:'查看3D预览',exact:true})).toHaveCount(0);
 await expect(page.getByLabel('制作名称',{exact:true})).toBeDisabled();
 await page.getByRole('button',{name:'确认布局并生成3D场景',exact:true}).click();
 await page.getByRole('button',{name:'查看3D预览',exact:true}).click();
 await expect(page.frameLocator('iframe').getByText('验收用私有预览')).toBeVisible();
 await page.getByRole('button',{name:'保存私有场景',exact:true}).click();
 await expect(page.getByText('场景已保存，可从制作记录中重新打开。',{exact:true})).toBeVisible();
 await page.reload();
 await expect(page.getByText('场景已保存，可从制作记录中重新打开。',{exact:true})).toBeVisible();
 expect(record.input.fields).toEqual(fields);expect(started).toBe(1);
 await page.setViewportSize({width:390,height:844});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.screenshot({path:'.build-cache/scene-production-mobile.png',fullPage:true});
});

test('refreshing a stopped status recovers the original layout without restarting generation',async({page})=>{
 const id='2174dab1-6a90-4c06-ba4f-ef96ddd47947';let reads=0;let starts=0;
 const record={id,input:{title:'状态恢复验收',targetPlaceId:'room',fields:{sourceText:'通用验收资料'}},phase:'Queued',createdAt:'2026-11-04T00:00:00Z',savedAt:null,status:{stage:'failed',label:'制作服务未启动',message:'旧启动状态',round:0,maxRounds:10,calls:0,tokens:0,progress:{generationCalls:0,repairAttempts:0,runtimeCheckAttempts:0,visualReviewAttempts:0,overallReviewAttempts:0},issues:[],layout:null},previewUrl:null};
 await page.route('**/api/**',async route=>{
  const name=new URL(route.request().url()).pathname.split('/').pop();
  if(name==='startsceneproduction')starts++;
  if(name==='getsceneproduction')reads++;
  const fresh={...record,status:{...record.status,stage:'awaiting_layout',label:'请先确认模型布局',message:'现有布局已恢复',calls:1}};
  await route.fulfill({json:name==='openlocalidentity'?{mode:'local-demo'}:name==='listsceneproductions'?[record]:fresh});
 });
 await page.goto('/create-scene');
 await page.getByRole('button',{name:/状态恢复验收/}).click();
 await expect(page.getByRole('heading',{name:'制作服务未启动',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'刷新制作状态',exact:true}).click();
 await expect(page.getByRole('button',{name:'确认布局并生成3D场景',exact:true})).toBeVisible();
 expect(starts).toBe(0);expect(reads).toBe(1);
});

test('explicit resume continues the same failed production with its recorded call count',async({page})=>{
 let resumes=0;let starts=0;
 const record:any={id:'2174dab1-6a90-4c06-ba4f-ef96ddd47947',input:{title:'原任务继续验收',targetPlaceId:'room',fields:{sourceText:'原资料'}},phase:'Queued',createdAt:'2026-11-04T00:00:00Z',savedAt:null,status:{stage:'failed',label:'任务已停止',message:'测试失败',round:0,maxRounds:10,calls:3,tokens:100,progress:{generationCalls:2,repairAttempts:0,runtimeCheckAttempts:0,visualReviewAttempts:0,overallReviewAttempts:0},issues:[],layout:null},previewUrl:null};
 await page.route('**/api/**',async route=>{
  const name=new URL(route.request().url()).pathname.split('/').pop();
  if(name==='startsceneproduction')starts++;
  if(name==='resumesceneproduction'){
   resumes++;expect(route.request().postDataJSON()).toEqual({productionId:record.id,expectedCalls:3});
   record.status={...record.status,stage:'generating',label:'正在继续原任务',message:'复用已有模型返回'};
  }
  await route.fulfill({json:name==='openlocalidentity'?{mode:'local-demo'}:name==='listsceneproductions'?[record]:record});
 });
 await page.goto('/create-scene');
 await page.getByRole('button',{name:/原任务继续验收/}).click();
 await page.getByRole('button',{name:'继续未完成的制作',exact:true}).click();
 await expect(page.getByRole('heading',{name:'正在继续原任务',exact:true})).toBeVisible();
 expect(resumes).toBe(1);expect(starts).toBe(0);
});
