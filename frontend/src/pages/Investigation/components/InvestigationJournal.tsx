import type { PreviewSession } from '../objects/PreviewSession';
import { previewJournal } from '../functions/previewRules';
export default function InvestigationJournal({ session }: { session: PreviewSession }) {
  const records = previewJournal(session);
  return <section aria-labelledby="journal-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <h2 id="journal-title" className="text-lg font-medium">调查记录</h2>
    {records.length === 0 ? <p className="mt-5 text-sm text-stone-400">调查尚未开始。</p> : <ol className="mt-5 space-y-5">{records.map((text, index) => <li key={text} className="flex gap-4"><span className="text-sm text-amber-200">{String(index + 1).padStart(2, '0')}</span><div><p className="text-sm text-stone-300">{text}</p><p className="mt-1 text-xs text-stone-500">调查开始 · 本地预览</p></div></li>)}</ol>}
  </section>;
}
