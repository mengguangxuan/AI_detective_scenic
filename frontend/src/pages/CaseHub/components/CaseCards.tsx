import type { CaseCatalogEntry } from '../../../apis/cases/objects/CaseCatalogEntry';
import InvestigationLink from '../../../app/components/InvestigationLink';

export default function CaseCards({ cases }: { cases: readonly CaseCatalogEntry[] }) {
  return <section aria-labelledby="catalog-title"><h2 id="catalog-title" className="text-xl font-medium">可调查的案件</h2>
    {cases.length ? <div className="mt-5 grid gap-5 md:grid-cols-2">{cases.map(entry => <article key={entry.caseId} className="rounded-2xl border border-white/15 bg-[#182421] p-6">
      <p className="text-xs tracking-widest text-amber-200">{entry.tags.join(' · ')}</p><h3 className="mt-3 text-xl font-medium">{entry.title}</h3>
      <p className="mt-4 text-sm leading-7 text-stone-300">{entry.summary}</p>
      <InvestigationLink href={`/cases/${encodeURIComponent(entry.caseId)}`} className="mt-6 inline-block rounded-lg border border-amber-200/60 px-5 py-2.5 text-sm text-amber-100">查看案件</InvestigationLink>
    </article>)}</div> : <p className="mt-4 text-stone-400">暂时没有开放的案件。</p>}
  </section>;
}
