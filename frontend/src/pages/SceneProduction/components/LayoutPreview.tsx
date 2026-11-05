import type {SceneProduction} from '../../../apis/sceneproduction/objects/SceneProduction';
type Layout=NonNullable<SceneProduction['status']['layout']>;
export default function LayoutPreview({layout}:{layout:Layout}){
 const items=layout.items;
 const span=Math.max(4,...items.flatMap(i=>[Math.abs(i.x)*2+i.width,Math.abs(i.z)*2+i.depth]));
 const factor=320/span;
 return <section aria-labelledby="layout-title" className="mt-5 rounded-xl border border-white/15 p-4"><h3 id="layout-title" className="font-medium">{layout.title}</h3>
  <p className="mt-2 text-xs leading-6 text-stone-400">俯视布局示意，矩形表示模型预留的部件范围；完整外形以生成后的3D预览为准。</p>
  <svg viewBox="-190 -190 380 380" role="img" aria-label="模型生成的场景布局俯视图" className="mx-auto mt-3 max-h-96 w-full rounded-lg bg-stone-950/50">
   {items.map((item,index)=><g key={item.id} transform={`translate(${item.x*factor} ${item.z*factor}) rotate(${item.rotationY*180/Math.PI})`}><rect x={-item.width*factor/2} y={-item.depth*factor/2} width={item.width*factor} height={item.depth*factor} fill={`hsl(${30+index*39} 35% 45% / .22)`} stroke={`hsl(${30+index*39} 50% 70%)`} strokeWidth="1"/><text textAnchor="middle" dominantBaseline="central" fill="#f6ecd8" fontSize="9">{index+1}</text></g>)}
  </svg>
  <ol className="mt-3 space-y-1 text-xs leading-6 text-stone-300">{items.map((item,index)=><li key={item.id}>{index+1}. {item.label}</li>)}</ol>
  <ul className="mt-4 space-y-2 text-sm leading-6 text-stone-300">{layout.decisions.map((text,index)=><li key={index}>{text}</li>)}</ul>
 </section>;
}
