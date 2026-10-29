import { investigationPages, type InvestigationPage } from '../objects/InvestigationPage';
import InvestigationLink from './InvestigationLink';
export default function InvestigationNavigation({ page }: { page: InvestigationPage | null }) {
  return <nav aria-label="调查导航" className="my-5 flex flex-wrap gap-2 border-b border-white/10 pb-5">
    {investigationPages.map(item => <InvestigationLink key={item.path} href={item.path}
      aria-current={page === item.path ? 'page' : undefined}
      className="rounded-lg border border-white/20 px-5 py-2.5 text-sm text-stone-300 hover:bg-white/10 aria-[current=page]:border-amber-200/60 aria-[current=page]:bg-amber-200/10 aria-[current=page]:text-amber-100">{item.label}</InvestigationLink>)}
  </nav>;
}
