import { useState } from 'react';
import type { AreaMap } from '../../apis/games/objects/AreaMap';
import { estimateRoute } from './functions/estimateRoute';
// Offsets belong to this published schematic's presentation, not to route distances.
const roadLabels: Record<string, { x: number; y: number }> = {
  farm_path: { x: 508, y: 565 },
  doctor_neighbor: { x: 508, y: 473 },
  studio_house: { x: 190, y: 385 },
};
export default function MapPage({ map }: { map: AreaMap | null }) {
  const [from, setFrom] = useState('study');
  const [to, setTo] = useState('old_hall');
  if (!map) return <section><h2 className="text-xl">地点地图</h2><p className="mt-3 text-sm text-stone-400">这份旧调查尚无地点地图；可保留原进度，另建调查查看新版。</p></section>;
  const locations = new Map(map.locations.map(location => [location.id, location]));
  const estimate = estimateRoute(map, from, to);
  const highlighted = new Set(estimate?.roads.map(road => road.id));
  return <section aria-labelledby="map-title" className="space-y-5">
    <div><h2 id="map-title" className="text-xl font-medium">地点地图</h2><p className="mt-2 text-sm leading-7 text-stone-400">{map.note} 图上道路交叉不表示另有路口。</p></div>
    <section aria-label="步行路线估算" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
      <div className="grid gap-4 sm:grid-cols-2"><div><label htmlFor="map-route-start" className="text-sm">起点</label><select id="map-route-start" value={from} onChange={event => setFrom(event.target.value)} className="mt-2 block w-full rounded-lg border border-white/25 bg-[#101918] px-3 py-2">{map.locations.map(location => <option key={location.id} value={location.id}>{location.name}</option>)}</select></div><div><label htmlFor="map-route-end" className="text-sm">终点</label><select id="map-route-end" value={to} onChange={event => setTo(event.target.value)} className="mt-2 block w-full rounded-lg border border-white/25 bg-[#101918] px-3 py-2">{map.locations.map(location => <option key={location.id} value={location.id}>{location.name}</option>)}</select></div></div>
      <p aria-live="polite" className="mt-4 text-sm leading-7 text-amber-100">{estimate ? `图中道路约 ${estimate.meters} 米，步行约 ${estimate.minutes} 分钟。` : '图中尚无连接这两个地点的道路记录。'}{estimate && estimate.roads.length > 0 ? ` 途经：${estimate.roads.map(road => road.label).join(' → ')}。` : ''}</p>
      <p className="mt-2 text-xs leading-6 text-stone-400">按每分钟约75米估算，未计停留。选择地点可更换终点；路线估算不代表人物已经走过。</p>
    </section>
    <div className="overflow-x-auto rounded-2xl border border-white/15 bg-[#14201e] p-2">
      <svg viewBox="0 0 1200 700" role="group" aria-label="圣玛丽米德地点示意图" className="min-w-[800px] w-full">
        <title>圣玛丽米德地点与道路距离示意</title><desc>点击或按回车选择终点；完整地点说明和道路距离也列在图下方。</desc>
        {map.roads.map(road => { const a = locations.get(road.from)!; const b = locations.get(road.to)!; const label = roadLabels[road.id] ?? { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 - 28 }; return <g key={road.id}><line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke={highlighted.has(road.id) ? '#f4d38b' : '#47615a'} strokeWidth={highlighted.has(road.id) ? 4 : 2} strokeDasharray={road.label.includes('小路') || road.label.includes('小径') ? '8 6' : undefined}/><text x={label.x} y={label.y} textAnchor="middle" fill={highlighted.has(road.id) ? '#ffe0a0' : '#b1c6bb'} fontSize="13" paintOrder="stroke" stroke="#14201e" strokeWidth="5" strokeLinejoin="round">约{road.meters}米</text></g>; })}
        {map.locations.map(location => <g key={location.id} role="button" aria-label={`地图地点：${location.name}`} tabIndex={0} onClick={() => setTo(location.id)} onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setTo(location.id); } }} className="cursor-pointer outline-none focus:[&>rect]:stroke-amber-100">
          <rect x={location.x - 72} y={location.y - 20} width="144" height="40" rx="9" fill={location.id === from || location.id === to ? '#3b4431' : '#1d302b'} stroke={location.id === from || location.id === to ? '#f4d38b' : '#69877a'} strokeWidth="2"/>
          <text x={location.x} y={location.y + 6} fill="#f4f2e7" textAnchor="middle" fontSize="16">{location.name}</text>
        </g>)}
      </svg>
    </div>
    <section aria-label="地点说明" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{map.locations.map(location => <article key={location.id} className="rounded-xl border border-white/10 bg-[#182421] p-4"><h3 className="text-sm font-medium text-amber-100">{location.name}</h3><p className="mt-2 text-sm leading-7 text-stone-300">{location.description}</p></article>)}</section>
    <details className="rounded-xl border border-white/10 p-4"><summary className="cursor-pointer text-sm text-stone-300">查看全部道路与距离</summary><ul className="mt-4 grid gap-3 sm:grid-cols-2">{map.roads.map(road => <li key={road.id} className="text-sm leading-7 text-stone-300">{locations.get(road.from)?.name} ↔ {locations.get(road.to)?.name}：约{road.meters}米 · {road.label}</li>)}</ul></details>
  </section>;
}
