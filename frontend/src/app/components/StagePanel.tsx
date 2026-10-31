import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';
import { investigationOutcome } from '../functions/investigationOutcome';
import InvestigationLink from './InvestigationLink';

type Props = { snapshot: Pick<InvestigationSnapshot, 'stage' | 'reasoning' | 'conclusion'>; compact?: boolean; reviewLink?: boolean };
export default function StagePanel({ snapshot, compact = false, reviewLink = true }: Props) {
  const outcome = investigationOutcome(snapshot);
  if (outcome) return <section aria-label="调查进度" className="mb-5 rounded-xl border border-amber-200/25 bg-amber-200/5 p-5 text-sm leading-7 text-stone-300">
    <h2 className="text-base font-medium text-amber-100">{outcome.heading}</h2>
    <p className="mt-2">{outcome.description}</p>
    {reviewLink ? <InvestigationLink href="/conclusion" className="mt-3 inline-block text-amber-200 underline underline-offset-4">{outcome.linkLabel}</InvestigationLink> : null}
  </section>;
  const stage = snapshot.stage;
  if (!stage) return null;
  return <section aria-label="阶段目标" className="mb-5 rounded-xl border border-amber-200/25 bg-amber-200/5 p-5 text-sm leading-7 text-stone-300">
    <h2 className="text-base font-medium text-amber-100">{!stage.hasTimeComparison ? '调查目标' : stage.completed ? '阶段完成 · 书房初查' : '当前阶段 · 书房初查'}</h2>
    {!compact ? <p className="mt-2">{stage.introduction}</p> : null}
    {!compact || stage.completed ? <p className="mt-2">{stage.completed ? stage.nextObjective ?? '初查笔记已保存，可回看材料和已有记录。' : stage.objective}</p> : null}
  </section>;
}
