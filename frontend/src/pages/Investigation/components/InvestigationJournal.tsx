import type { InvestigationLogEntry } from '../../../apis/games/objects/InvestigationLogEntry';
export default function InvestigationJournal({ records }: { records: readonly InvestigationLogEntry[] }) {
  return <section aria-labelledby="journal-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <h2 id="journal-title" className="text-lg font-medium">调查记录</h2>
    {records.length === 0 ? <p className="mt-5 text-sm text-stone-400">调查尚未开始。</p> : <ol className="mt-5 space-y-5">{records.map(record => <li key={record.id} className="flex gap-4"><span className="text-sm text-amber-200">{String(record.sequence).padStart(2,'0')}</span><div><p className="whitespace-pre-wrap text-sm text-stone-300">{record.text}</p><p className="mt-1 text-xs text-stone-500">{record.storyTimeLabel}</p></div></li>)}</ol>}
  </section>;
}
