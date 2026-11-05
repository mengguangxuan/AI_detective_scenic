import {useRef,useState} from 'react';
type Props={text:string;disabled:boolean;onChange:(value:string)=>void};
const fields=[['sceneName','地点／房间名称'],['era','时代与地点背景'],['storyTime','当前时间或调查阶段'],['sourceText','原始场景资料'],['requirements','必须保留的事实与空间关系'],['atmosphere','希望的氛围（可选）']] as const;
export default function AuthorFields({text,disabled,onChange}:Props){
 const [mode,setMode]=useState<'form'|'json'>('form'),[error,setError]=useState('');
 const file=useRef<HTMLInputElement>(null);
 let parsed:Record<string,unknown>|null=null;try{const value:unknown=JSON.parse(text);if(value&&typeof value==='object'&&!Array.isArray(value))parsed=value as Record<string,unknown>;}catch{}
 const simple=parsed!==null&&fields.some(([key])=>key in parsed);
 function patch(key:string,value:string){onChange(JSON.stringify({...parsed,[key]:value},null,2));}
 async function upload(selected:File|undefined){if(!selected)return;try{if(selected.size>120000)throw new Error('作者资料文件最多120KB。');const value:unknown=JSON.parse(await selected.text());if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('请上传JSON对象格式的作者字段。');onChange(JSON.stringify(value,null,2));setMode('json');setError('');}catch(e){setError(e instanceof Error?e.message:'读取资料失败。');}finally{if(file.current)file.current.value='';}}
 return <fieldset disabled={disabled} className="space-y-4 disabled:opacity-60"><legend className="mb-3 font-medium">作者原始字段</legend>
  <div className="flex flex-wrap gap-3"><button type="button" onClick={()=>setMode(mode==='json'&&simple?'form':'json')} className="rounded-lg border border-white/20 px-3 py-2 text-sm">{mode==='json'&&simple?'按字段填写':'查看原始JSON'}</button><button type="button" onClick={()=>file.current?.click()} className="rounded-lg border border-white/20 px-3 py-2 text-sm">导入作者JSON</button><input ref={file} type="file" accept=".json,application/json" aria-label="导入作者字段文件" className="hidden" onChange={e=>void upload(e.target.files?.[0])}/></div>
  <p className="text-xs leading-6 text-stone-400">资料会原样保存并交给模型。上传已有作者字段时，未知字段也会保留。</p>
  {mode==='form'&&simple?fields.map(([key,label])=><label key={key} className="block text-sm">{label}{key==='sourceText'||key==='requirements'||key==='atmosphere'?<textarea aria-label={label} value={typeof parsed?.[key]==='string'?parsed[key] as string:''} onChange={e=>patch(key,e.target.value)} rows={key==='sourceText'?7:3} className="mt-2 w-full rounded-lg border border-white/20 bg-[#101918] p-3 leading-7"/>:<input aria-label={label} value={typeof parsed?.[key]==='string'?parsed[key] as string:''} onChange={e=>patch(key,e.target.value)} className="mt-2 w-full rounded-lg border border-white/20 bg-[#101918] p-3"/>}</label>):<label className="block text-sm">原始JSON字段<textarea aria-label="原始JSON字段" value={text} onChange={e=>onChange(e.target.value)} spellCheck={false} rows={20} className="mt-2 w-full rounded-lg border border-white/20 bg-[#101918] p-3 font-mono text-xs leading-6"/></label>}
  {error?<p role="alert" className="text-sm text-amber-100">{error}</p>:null}
 </fieldset>;
}
