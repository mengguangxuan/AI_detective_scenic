import type { ClueView } from '../../../apis/games/objects/ClueView';
export default function CluePanel({ clues }: { clues: readonly ClueView[] }) {
  return <section aria-labelledby="clue-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <div className="flex items-center justify-between"><h2 id="clue-title" className="text-lg font-medium">线索笔记</h2><span className="text-sm text-amber-200">{String(clues.length).padStart(2,'0')}</span></div>
    {clues.length ? clues.map(clue => <article key={clue.id} className="mt-5 border-l-2 border-amber-200 pl-4"><h3 className="font-medium">{clue.title}</h3><p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-stone-300">{clue.text}</p><p className="mt-3 text-xs text-stone-400">来源：本次调查获准的信息</p></article>)
      : <p className="mt-5 text-sm leading-7 text-stone-400">尚未记录线索。检查现场后，获得的信息会出现在这里。</p>}
  </section>;
}
