import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';
type Props = { clock: InvestigationSnapshot['clock']; disabled: boolean; onWait: (minutes: number) => void };
export default function CaseClockPanel({ clock, disabled, onWait }: Props) {
  if (!clock) return null;
  return <section aria-label="案件时钟" className="mb-5 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-white/15 bg-[#182421] p-4">
    <div><p className="text-sm font-medium text-amber-100">案件时间 · {clock.label}</p><p className="mt-1 text-xs leading-6 text-stone-400">一般调查行动约 {clock.actionMinutes} 分钟。查看地图、切换页面与刷新不计时。</p></div>
    {clock.phaseEnd ? <p className="text-xs text-stone-400">本阶段开放至{clock.phaseEnd}。后续阶段暂未开放。</p> : null}
    {clock.targets.length ? <p className="text-xs text-stone-400">后续时间安排：{clock.targets.map(t => `${t.label} · ${t.time}`).join('；')}（待开放）</p> : null}
    <div className="flex flex-wrap gap-2">{[5, 15].map(minutes => <button key={minutes} type="button" disabled={disabled || minutes > clock.maximumWaitMinutes} onClick={() => onWait(minutes)} className="rounded-lg border border-amber-200/40 px-3 py-2 text-sm text-amber-100 disabled:opacity-40">整理并等待{minutes}分钟</button>)}</div>
  </section>;
}
