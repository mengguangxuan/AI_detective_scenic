import type { InvestigationPageState } from '../../features/investigation/objects/InvestigationPageState';
import type { useInvestigation } from '../../features/investigation/hooks/useInvestigation';
import type { TimeComparisonDraft } from './objects/TimeComparisonDraft';
import StatementComparisons from './components/StatementComparisons';
import CluePanel from './components/CluePanel';
import TimeComparisonPanel from './components/TimeComparisonPanel';
import InvestigationLink from '../../app/components/InvestigationLink';
type Props = { ready: Extract<InvestigationPageState, { kind: 'ready' }>;
  onCompare: ReturnType<typeof useInvestigation>['compare']; draft: TimeComparisonDraft;
  onDraftChange: (draft: TimeComparisonDraft) => void };
export default function CluesPage({ ready, onCompare, draft, onDraftChange }: Props) {
  return <section aria-labelledby="clues-page-title" className="space-y-5">
    <div><h2 id="clues-page-title" className="text-xl font-medium">线索整理</h2><p className="mt-2 text-sm leading-6 text-stone-400">查看已经取得的材料，整理你的时间核对。</p></div>
    <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(300px,420px)]">
      <CluePanel clues={ready.snapshot.clues}/>
      <TimeComparisonPanel clues={ready.snapshot.clues} disabled={ready.mutation.kind !== 'idle'} completed={ready.snapshot.stage?.completed ?? false}
        notice={ready.notice} onCompare={onCompare} draft={draft} onDraftChange={onDraftChange}/>
    </div>
    <StatementComparisons comparisons={ready.snapshot.statementComparisons}/>
    <InvestigationLink href="/journal" className="inline-block text-sm text-amber-200 underline underline-offset-4">查看调查日志（{ready.snapshot.journal.length}条）</InvestigationLink>
  </section>;
}
