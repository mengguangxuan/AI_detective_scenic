import {useState} from 'react';
import type {SceneProduction} from '../../../apis/sceneproduction/objects/SceneProduction';
import LayoutPreview from './LayoutPreview';
type Props={production:SceneProduction;busy:boolean;dirty:boolean;onStart:()=>void;onConfirm:()=>void;onSave:()=>void;onRefresh:()=>void;onResume:()=>void};
export default function ProductionPanel({production:p,busy,dirty,onStart,onConfirm,onSave,onRefresh,onResume}:Props){
 const [showPreview,setShowPreview]=useState(false);
 const s=p.status,progress=s.progress;
 return <section aria-labelledby="production-status" className="rounded-2xl border border-white/15 bg-[#182421] p-6"><h2 id="production-status" className="text-xl font-medium">{s.label}</h2><p className="mt-3 text-sm leading-7 text-stone-300">{s.message}</p>
  <dl className="mt-5 grid grid-cols-2 gap-3 text-sm"><div><dt className="text-stone-400">运行检查</dt><dd>{progress.runtimeCheckAttempts} 次</dd></div><div><dt className="text-stone-400">视觉审核</dt><dd>{progress.visualReviewAttempts} 次</dd></div><div><dt className="text-stone-400">整体复核</dt><dd>{progress.overallReviewAttempts} 次</dd></div><div><dt className="text-stone-400">处理轮数</dt><dd>{s.round} / {s.maxRounds}</dd></div></dl>
  {p.phase==='Draft'?<><button disabled={busy||dirty} onClick={onStart} className="mt-5 rounded-lg bg-amber-200 px-5 py-3 text-stone-950 disabled:opacity-40">开始模型制作</button><p className="mt-2 text-xs leading-6 text-stone-400">提交后会调用模型并产生用量，先生成布局供你确认。</p></>:null}
  {p.phase!=='Draft'?<button disabled={busy} onClick={onRefresh} className="mt-5 rounded-lg border border-white/20 px-4 py-2 disabled:opacity-40">刷新制作状态</button>:null}
  {p.phase==='Queued'&&s.stage==='failed'&&s.round<s.maxRounds&&s.calls<88?<div className="mt-4"><button disabled={busy} onClick={onResume} className="rounded-lg bg-amber-200 px-4 py-2 text-stone-950 disabled:opacity-40">继续未完成的制作</button><p className="mt-2 text-xs leading-6 text-stone-400">沿用原资料、布局和匹配的模型返回；剩余步骤会调用模型并产生用量。</p></div>:null}
  {s.layout?<LayoutPreview layout={s.layout}/>:null}
  {s.stage==='awaiting_layout'?<button disabled={busy} onClick={onConfirm} className="mt-5 rounded-lg bg-amber-200 px-5 py-3 text-stone-950 disabled:opacity-40">确认布局并生成3D场景</button>:null}
  {s.issues.length?<section aria-label="模型检查意见" className="mt-5 border-t border-white/10 pt-4"><h3 className="font-medium">待处理问题</h3><ul className="mt-3 space-y-3 text-sm leading-7 text-amber-100">{s.issues.map((issue,index)=><li key={index}>{issue.message}</li>)}</ul></section>:null}
  {s.stage==='passed'?<div className="mt-5 space-y-3"><p className="text-sm text-emerald-200">运行、视觉和整体审核已通过。</p>{p.previewUrl?<><button onClick={()=>setShowPreview(value=>!value)} className="rounded-lg border border-amber-200 px-4 py-2">{showPreview?'收起3D预览':'查看3D预览'}</button> <a href={p.previewUrl} target="_blank" rel="noopener noreferrer" className="text-sm text-amber-100 underline">单独打开预览</a>{showPreview?<iframe title="已通过模型审核的场景预览" src={p.previewUrl} sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer" className="h-[32rem] w-full rounded-xl border border-white/15"/>:null}</>:<p role="status" className="text-sm text-stone-400">正在准备预览入口…</p>}{p.phase!=='Saved'?<button disabled={busy} onClick={onSave} className="block rounded-lg bg-amber-200 px-5 py-3 text-stone-950 disabled:opacity-40">保存私有场景</button>:<p className="text-sm text-emerald-200">场景已保存，可从制作记录中重新打开。</p>}</div>:null}
  <p className="mt-5 text-xs leading-6 text-stone-500">此处保存的是场景制作成果；玩家搜查、线索与存档接入将在后续完成。</p>
 </section>;
}
