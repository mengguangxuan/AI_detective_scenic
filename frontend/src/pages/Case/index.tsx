import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';
import InvestigationLink from '../../app/components/InvestigationLink';
import StagePanel from '../../app/components/StagePanel';
import type { PointId } from '../../objects/PointId';

export default function CasePage({ snapshot, onSelect }: { snapshot: InvestigationSnapshot; onSelect: (point: PointId) => void }) {
  return <section aria-labelledby="case-page-title" className="space-y-5">
    <div><h2 id="case-page-title" className="text-xl font-medium">案件简介</h2><p className="mt-2 text-sm leading-6 text-stone-400">先了解眼前的案情，再决定从哪里开始调查。</p></div>
    <StagePanel stage={snapshot.stage}/>
    <section aria-labelledby="case-progress-title" className="rounded-2xl border border-white/10 bg-[#182421] p-5 sm:p-7">
      <div className="flex flex-wrap items-start justify-between gap-5">
        <div><h3 id="case-progress-title" className="text-lg font-medium">这次调查</h3><p className="mt-3 text-sm text-stone-300">当前现场：{snapshot.scene.placeName}</p></div>
        <InvestigationLink href="/scene" className="rounded-lg bg-amber-200 px-5 py-3 text-sm font-medium text-stone-950 hover:bg-amber-100">{snapshot.sequence === 0 ? '开始现场调查' : '返回现场调查'}</InvestigationLink>
      </div>
      <dl className="mt-6 grid grid-cols-3 gap-3 border-t border-white/10 pt-5 text-center">
        <div><dt className="text-xs text-stone-400">已获材料</dt><dd className="mt-2 text-2xl text-amber-100">{snapshot.clues.length}</dd></div>
        <div><dt className="text-xs text-stone-400">可询问人物</dt><dd className="mt-2 text-2xl text-amber-100">{snapshot.witnesses.length}</dd></div>
        <div><dt className="text-xs text-stone-400">调查记录</dt><dd className="mt-2 text-2xl text-amber-100">{snapshot.journal.length}</dd></div>
      </dl>
      <p className="mt-5 text-sm leading-7 text-stone-400">调查结果会自动保存。你可以随时切换页面，回看材料和对话，再继续检查现场。</p>
      {snapshot.police ? <InvestigationLink href="/police" className="mt-4 inline-block text-sm text-amber-200 underline underline-offset-4">向警方了解情况与医生的初查意见</InvestigationLink> : null}
    </section>
    {snapshot.people.length ? <section aria-labelledby="case-people-title" className="space-y-4">
      <div><h3 id="case-people-title" className="text-lg font-medium">案中人物</h3><p className="mt-2 text-sm leading-7 text-stone-400">先认识相关人物，详细经过可以在交谈中了解。</p></div>
      <div className="grid items-start gap-4 md:grid-cols-2">{snapshot.people.map(person => {
        const witness = snapshot.witnesses.find(w => w.id === person.witness);
        return <article key={person.id} aria-labelledby={`brief-${person.id}`} className="rounded-2xl border border-white/10 bg-[#182421] p-5">
          <h4 id={`brief-${person.id}`} className="font-medium text-amber-100">{person.name}</h4>
          <p className="mt-2 text-sm leading-7 text-stone-300">{person.identity}</p>
          <dl className="mt-3 space-y-2 text-sm leading-7"><div><dt className="inline text-stone-500">与死者的关系：</dt><dd className="inline text-stone-300">{person.victimRelation}</dd></div><div><dt className="inline text-stone-500">与案件的关系：</dt><dd className="inline text-stone-300">{person.caseRelation}</dd></div></dl>
          {witness ? <InvestigationLink href={witness.point === snapshot.police?.point ? '/police' : '/interviews'} onNavigate={() => onSelect(witness.point)} className="mt-3 inline-block text-sm text-amber-200 underline underline-offset-4">询问{person.name}</InvestigationLink> : null}
        </article>;
      })}</div>
    </section> : null}
    <div className="grid gap-4 md:grid-cols-3">
      <article className="rounded-2xl border border-white/10 p-5"><h3 className="font-medium">认识相关人物</h3><p className="my-3 text-sm leading-7 text-stone-400">查看人物的公开身份和你已记录的答复，选择谈话对象。</p><InvestigationLink href="/people" className="text-sm text-amber-200 underline underline-offset-4">查看人物档案</InvestigationLink></article>
      <article className="rounded-2xl border border-white/10 p-5"><h3 className="font-medium">整理手中的材料</h3><p className="my-3 text-sm leading-7 text-stone-400">检查得到的内容会收在线索笔记里，你可以在那里进行时间核对。</p><InvestigationLink href="/clues" className="text-sm text-amber-200 underline underline-offset-4">前往线索整理</InvestigationLink></article>
      <article className="rounded-2xl border border-white/10 p-5"><h3 className="font-medium">回顾调查经过</h3><p className="my-3 text-sm leading-7 text-stone-400">按调查顺序查找已经执行的操作，回看每一步新取得的材料。</p><InvestigationLink href="/journal" className="text-sm text-amber-200 underline underline-offset-4">查看调查日志</InvestigationLink></article>
    </div>
  </section>;
}
