import {useEffect,useState} from 'react';
import InvestigationLink from '../../app/components/InvestigationLink';
import {sceneAuthorInputSchema,type SceneProduction} from '../../apis/sceneproduction/objects/SceneProduction';
import {useSceneProduction} from './hooks/useSceneProduction';
import AuthorFields from './components/AuthorFields';
import ProductionPanel from './components/ProductionPanel';
const emptyFields={sceneName:'',era:'',storyTime:'',sourceText:'',requirements:'',atmosphere:''};
type Editor={id:string;title:string;target:string;fields:string};
function blank():Editor{return{id:crypto.randomUUID(),title:'',target:'scene',fields:JSON.stringify(emptyFields,null,2)};}
function editor(row:SceneProduction){return{id:row.id,title:row.input.title,target:row.input.targetPlaceId,fields:JSON.stringify(row.input.fields,null,2)};}
export default function SceneProductionPage(){
 const hub=useSceneProduction();const [form,setForm]=useState(blank),[dirty,setDirty]=useState(false),[error,setError]=useState('');
 useEffect(()=>{document.title='场景制作 · AI侦探剧场';},[]);
 const selectedId=hub.selected?.id;
 useEffect(()=>{if(hub.selected&&!dirty)setForm(editor(hub.selected));},[selectedId,hub.selected,dirty]);
 const frozen=hub.selected?.phase!=='Draft'&&hub.selected!==null;
 function change(patch:Partial<typeof form>){setForm(current=>({...current,...patch}));setDirty(true);setError('');}
 function choose(row:SceneProduction){if(dirty){setError('资料有未保存修改，请先保存草稿或放弃修改。');return;}hub.choose(row);setForm(editor(row));setDirty(false);setError('');}
 async function save(){try{const fields:unknown=JSON.parse(form.fields);const input=sceneAuthorInputSchema.parse({title:form.title,targetPlaceId:form.target,fields});if(placeOptions.length&&!placeOptions.some(p=>p.id===input.targetPlaceId))throw new Error('请选择资料中的目标场景。');if(!Object.keys(input.fields).length)throw new Error('请填写或导入场景资料。');if(Object.hasOwn(input.fields,'sourceText')&&typeof input.fields.sourceText==='string'&&!input.fields.sourceText.trim())throw new Error('请填写原始场景资料。');const result=await hub.save(form.id,input);if(result){setForm(editor(result));setDirty(false);}}catch(e){setError(e instanceof SyntaxError?'原始字段不是有效JSON，请检查后保存。':e instanceof Error&&e.name!=='ZodError'?e.message:'请填写制作名称、有效场景编号与JSON对象资料。');}}
 function create(copy=false){if(dirty){setError('请先保存草稿或放弃修改。');return;}const source=copy&&hub.selected?{...editor(hub.selected),id:crypto.randomUUID()}:blank();hub.choose(null);setForm(source);setDirty(copy);setError('');}
 let placeOptions:{id:string;name:string}[]=[];try{const value:unknown=JSON.parse(form.fields);if(value&&typeof value==='object'&&'places' in value&&Array.isArray(value.places))placeOptions=value.places.flatMap(p=>p&&typeof p==='object'&&typeof p.id==='string'?[{id:p.id,name:typeof p.name==='string'?p.name:p.id}]:[]);}catch{}
 return <main className="min-h-screen bg-[#101918] px-4 py-8 text-stone-100 sm:px-8"><div className="mx-auto max-w-7xl">
  <header className="flex flex-wrap items-center justify-between gap-4 border-b border-white/15 pb-6"><div><p className="text-xs tracking-widest text-amber-200">AI DETECTIVE THEATRE</p><h1 className="mt-2 text-2xl font-medium">场景制作</h1></div><nav className="flex gap-5 text-sm"><InvestigationLink href="/">首页</InvestigationLink><InvestigationLink href="/cases">案件区</InvestigationLink><InvestigationLink href="/case">返回调查</InvestigationLink></nav></header>
  <p className="my-6 max-w-4xl text-sm leading-8 text-stone-300">填写原始资料后保存草稿，模型先提出布局；确认后生成部件和组装器，再查看实际画面的审核结果。通过后可以预览并保存私有场景。</p>
  {hub.state.kind==='loading'?<p role="status">正在读取你的制作记录…</p>:hub.state.kind==='failed'?<section role="alert"><p>{hub.state.message}</p><button onClick={()=>void hub.reload()} className="mt-3 underline">重新读取</button></section>:<>
  <div className="mb-6 flex flex-wrap gap-3"><button disabled={hub.busy} onClick={()=>create()} className="rounded-lg border border-white/20 px-4 py-2">新建场景草稿</button>{hub.selected?<button disabled={hub.busy} onClick={()=>create(true)} className="rounded-lg border border-white/20 px-4 py-2">复制为新草稿</button>:null}</div>
  <div className="grid items-start gap-6 xl:grid-cols-2"><section aria-labelledby="author-input-title" className="rounded-2xl border border-white/15 bg-[#182421] p-6"><h2 id="author-input-title" className="text-xl font-medium">{frozen?'本次制作使用的原始资料':'填写场景资料'}</h2>
   <p role="status" className="mt-2 text-xs text-stone-400">{dirty?'有未保存修改':hub.selected?'资料已保存到服务器':'新草稿，尚未保存'}</p>
   <label className="mt-5 block text-sm">制作名称<input disabled={hub.busy||frozen} value={form.title} maxLength={120} onChange={e=>change({title:e.target.value})} className="mt-2 w-full rounded-lg border border-white/20 bg-[#101918] p-3 disabled:opacity-60"/></label>
   <label className="my-5 block text-sm">目标场景{placeOptions.length?<select aria-label="目标场景" disabled={hub.busy||frozen} value={form.target} onChange={e=>change({target:e.target.value})} className="mt-2 w-full rounded-lg border border-white/20 bg-[#101918] p-3"><option value="scene">请选择资料中的场景</option>{placeOptions.map(p=><option key={p.id} value={p.id}>{p.name}（{p.id}）</option>)}</select>:<><input aria-label="目标场景" disabled={hub.busy||frozen} value={form.target} onChange={e=>change({target:e.target.value})} className="mt-2 w-full rounded-lg border border-white/20 bg-[#101918] p-3 disabled:opacity-60"/><span className="mt-1 block text-xs text-stone-400">用于关联场景的编号，使用字母、数字、下划线或短横线。</span></>}</label>
   <AuthorFields text={form.fields} disabled={hub.busy||frozen} onChange={fields=>change({fields})}/>
   {!frozen?<div className="mt-5 flex gap-3"><button disabled={hub.busy} onClick={()=>void save()} className="rounded-lg bg-amber-200 px-5 py-3 text-stone-950 disabled:opacity-40">保存草稿</button>{dirty?<button disabled={hub.busy} onClick={()=>{setForm(hub.selected?editor(hub.selected):blank());setDirty(false);setError('');}} className="rounded-lg border border-white/20 px-4 py-2">放弃未保存修改</button>:null}</div>:<p className="mt-4 text-xs leading-6 text-stone-400">制作已开始，资料已冻结；需要修改时可复制为新草稿。</p>}
  </section>
  <div>{hub.selected?<ProductionPanel key={hub.selected.id} production={hub.selected} busy={hub.busy} dirty={dirty} onStart={()=>void hub.start()} onConfirm={()=>void hub.confirm()} onSave={()=>void hub.saveArtifact()} onRefresh={()=>void hub.refresh()} onResume={()=>void hub.resume()}/>:<section className="rounded-2xl border border-dashed border-white/20 p-8 text-sm leading-7 text-stone-400">保存草稿后，可以提交模型制作。尚未提交的资料不会调用模型。</section>}</div></div>
  {error||hub.notice?<p role="alert" className="mt-5 rounded-lg border border-amber-200/30 p-4 text-sm text-amber-100">{error||hub.notice}</p>:null}
  <section aria-labelledby="saved-productions" className="mt-10"><h2 id="saved-productions" className="text-xl font-medium">我的制作记录</h2>{!hub.entries.length?<p className="mt-3 text-sm text-stone-400">还没有保存的场景草稿。</p>:<ul className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{hub.entries.map(row=><li key={row.id}><button disabled={hub.busy} onClick={()=>choose(row)} aria-pressed={hub.selected?.id===row.id} className="w-full rounded-xl border border-white/15 p-4 text-left disabled:opacity-40"><span className="block font-medium">{row.input.title}</span><span className="mt-2 block text-xs text-stone-400">{row.phase==='Saved'?'已保存私有场景':row.status.label}</span></button></li>)}</ul>}</section>
  </>}
 </div></main>;
}
