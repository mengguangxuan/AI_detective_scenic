import type { ClueView } from '../../../apis/games/objects/ClueView';
import type { InvestigationLogEntry } from '../../../apis/games/objects/InvestigationLogEntry';
export default function CluePanel({ clues, journal }: { clues: readonly ClueView[]; journal: readonly InvestigationLogEntry[] }) {
  const sources = new Map(journal.flatMap(entry => entry.clueIds.map(id => [id, entry] as const)).reverse());
  return <section aria-labelledby="clue-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <div className="flex items-center justify-between"><h2 id="clue-title" className="text-lg font-medium">线索笔记</h2><span className="text-sm text-amber-200">{String(clues.length).padStart(2,'0')}</span></div>
    {clues.length ? clues.map(clue => <article key={clue.id} className="mt-5 border-l-2 border-amber-200 pl-4"><h3 className="font-medium">{clue.title}</h3><p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-stone-300">{clue.text}</p>{sources.has(clue.id) ? <details className="mt-3 text-sm text-stone-400"><summary className="cursor-pointer">取得记录 · {sources.get(clue.id)!.storyTimeLabel}</summary><p className="mt-2 whitespace-pre-wrap leading-7">{sources.get(clue.id)!.text}</p></details> : null}</article>)
      : <p className="mt-5 text-sm leading-7 text-stone-400">尚未记录线索。检查现场后，获得的信息会出现在这里。</p>}
  </section>;
}
