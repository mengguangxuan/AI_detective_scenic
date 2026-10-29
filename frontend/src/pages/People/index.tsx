import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';
import type { PointId } from '../../objects/PointId';
import InvestigationLink from '../../app/components/InvestigationLink';

type Props = { snapshot: InvestigationSnapshot; onSelect: (point: PointId) => void };
export default function PeoplePage({ snapshot, onSelect }: Props) {
  return <section aria-labelledby="people-page-title" className="space-y-5">
    <div><h2 id="people-page-title" className="text-xl font-medium">人物档案</h2><p className="mt-2 text-sm leading-6 text-stone-400">查看当前可接触人物的身份，以及你已经取得的答复。</p></div>
    {snapshot.witnesses.length ? <div className="grid items-start gap-5 lg:grid-cols-2">{snapshot.witnesses.map(person => {
      const findings = snapshot.points.find(point => point.id === person.point)?.findings ?? [];
      const turns = snapshot.interviews.filter(turn => turn.witness === person.id);
      return <article key={person.id} aria-labelledby={`person-${person.id}`} className="rounded-2xl border border-white/10 bg-[#182421] p-5 sm:p-7">
        <p className="text-xs tracking-widest text-amber-200">可接触人物</p>
        <h3 id={`person-${person.id}`} className="mt-3 text-xl font-medium">{person.name}</h3>
        <p className="mt-4 text-sm leading-7 text-stone-300">{person.background}</p>
        <p className="mt-4 text-xs text-stone-400">已记录材料 {findings.length} 份 · 自由提问 {turns.length} 条</p>
        <InvestigationLink href={person.point === snapshot.police?.point ? "/police" : "/interviews"} onNavigate={() => onSelect(person.point)} className="mt-5 inline-block rounded-lg border border-amber-200/60 px-4 py-2 text-sm text-amber-100 hover:bg-amber-200/10">询问{person.name}</InvestigationLink>
        <section aria-label={`${person.name}的已获材料`} className="mt-6 border-t border-white/10 pt-5">
          <h4 className="text-sm font-medium">已获材料</h4>
          {findings.length ? findings.map(finding => <details key={finding.id} className="mt-4 border-l-2 border-amber-200/50 pl-4"><summary className="cursor-pointer text-sm text-stone-200">{finding.title}</summary><p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-stone-300">{finding.text}</p></details>) : <p className="mt-3 text-sm leading-7 text-stone-400">还没有记录这位人物的证词。可以先与其交谈。</p>}
        </section>
      </article>;
    })}</div> : <p className="rounded-xl border border-white/10 p-5 text-sm text-stone-400">当前还没有可接触的人物。</p>}
  </section>;
}
