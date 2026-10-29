import type { StatementComparisonView } from '../../../apis/games/objects/StatementComparisonView';
import InvestigationLink from '../../../app/components/InvestigationLink';
type Version = StatementComparisonView['rows'][number]['initial'];
function Statement({ version }: { version: Version }) {
  return version ? <><p className="text-xs text-stone-400">第 {version.sequence} 条记录 · {version.recordedAt}</p><p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-stone-200">{version.clue.text}</p></> : <p className="text-sm leading-7 text-stone-500">这一部分尚未取得记录。</p>;
}
export default function StatementComparisons({ comparisons }: { comparisons: readonly StatementComparisonView[] }) {
  if (comparisons.length === 0) return null;
  return <section aria-label="供述版本对照" className="space-y-4">
    <div><h2 className="text-lg font-medium">供述版本对照</h2><p className="mt-2 text-sm leading-7 text-stone-400">按实际取得的记录并列查看，各人的说法仍需独立证据核实。</p></div>
    {comparisons.map(comparison => <article key={comparison.witness} aria-label={comparison.title} className="rounded-2xl border border-white/10 bg-[#182421] p-5">
      <h3 className="font-medium text-amber-100">{comparison.title}</h3>
      <div className="mt-4 space-y-5">{comparison.rows.map(row => <section key={row.label} className="border-t border-white/10 pt-4"><h4 className="text-sm font-medium text-stone-300">{row.label}</h4><div className="mt-3 grid gap-4 md:grid-cols-2"><div className="rounded-xl bg-stone-950/25 p-4"><p className="mb-3 text-sm text-amber-100">首轮记录</p><Statement version={row.initial}/></div><div className="rounded-xl bg-stone-950/25 p-4"><p className="mb-3 text-sm text-amber-100">补充记录</p><Statement version={row.revised}/></div></div></section>)}</div>
      {comparison.nextObjective ? <p className="mt-5 border-t border-white/10 pt-4 text-sm leading-7 text-amber-100">可继续核查：{comparison.nextObjective}</p> : null}
    </article>)}
    <InvestigationLink href="/interviews" className="inline-block text-sm text-amber-200 underline underline-offset-4">返回人物问询，继续追问</InvestigationLink>
  </section>;
}
