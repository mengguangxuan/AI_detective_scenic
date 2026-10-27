type Props = { letterRead: boolean };
export default function CluePanel({ letterRead }: Props) {
  return <section aria-labelledby="clue-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <div className="flex items-center justify-between"><h2 id="clue-title" className="text-lg font-medium">线索笔记</h2><span className="text-sm text-amber-200">{letterRead ? '01' : '00'}</span></div>
    {letterRead ? <article className="mt-5 border-l-2 border-amber-200 pl-4"><h3 className="font-medium">抽屉中的信</h3><p className="mt-3 text-sm leading-7 text-stone-300">信上写着：今晚九点见。</p><p className="mt-3 text-xs text-stone-400">来源：检查书桌抽屉中的信件 · 预览数据</p></article>
      : <p className="mt-5 text-sm leading-7 text-stone-400">尚未记录线索。检查现场后，获得的信息会出现在这里。</p>}
  </section>;
}
