import type { InvestigationSnapshot } from '../../../apis/games/objects/InvestigationSnapshot';
import type { ReasoningProgress } from '../../../apis/games/objects/ReasoningProgress';

type Props = { ending: NonNullable<ReasoningProgress['ending']>; attempt: ReasoningProgress['attempts'][number] | undefined;
  people: InvestigationSnapshot['people']; clues: InvestigationSnapshot['clues'] };
export default function ReviewComparison({ ending, attempt, people, clues }: Props) {
  return <section aria-label="本案收尾与复盘" className="space-y-5 rounded-xl border border-amber-200/25 p-5">
    <h3 className="text-lg text-amber-100">{ending.mode === 'Solved' ? '已记录推理成立后的结案' : '已主动查看解答 · 本次未计为破案'}</h3>
    <div className="grid items-start gap-5 lg:grid-cols-2">
      <section aria-label="结案前最后一次推理" className="min-w-0 space-y-3 rounded-lg border border-white/15 p-4">
        <h4 className="font-medium">你的最后一次推理</h4>
        {attempt ? <>
          <p className="text-sm text-stone-400">怀疑人物：{attempt.suspects.map(id => people.find(p => p.id === id)?.name ?? id).join('、')}</p>
          <p className="whitespace-pre-wrap break-words text-sm leading-7 text-stone-300">{attempt.explanation}</p>
          <h5 className="text-sm font-medium">本次引用的材料</h5>
          <ul className="space-y-1 text-sm leading-6 text-stone-400">{attempt.evidence.map(id => <li key={id}>{clues.find(c => c.id === id)?.title ?? id}</li>)}</ul>
          <p className="text-xs leading-6 text-stone-400">{ending.mode === 'Solved' ? '这里保留通过评审时的提交原文和引用。' : '这里保留最后一次提交原文；主动查看解答不计为推理破案。'}</p>
        </> : <p className="text-sm leading-7 text-stone-400">你在提交推理之前选择了查看解答，本局没有已提交的推理原文。</p>}
      </section>
      <section aria-label="作者解释" className="min-w-0 rounded-lg border border-white/15 p-4">
        <h4 className="font-medium">作者解释</h4>
        <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-7 text-stone-300">{ending.review}</p>
      </section>
    </div>
    {ending.sections.length > 0 && <section aria-label="结案时的材料对照" className="space-y-4 border-t border-white/15 pt-5">
      <h4 className="font-medium">逐项对照解释与材料</h4>
      <p className="text-sm leading-7 text-stone-400">以下保留结案时已取得的相关材料，并标明最后一次提交是否引用。列出材料不等于它证明了该项全部解释；继续调查取得的新材料不会改写这份对照。</p>
      {ending.sections.map(section => <article key={section.title} aria-label={section.title} className="space-y-3 rounded-lg border border-white/15 p-4">
        <h5 className="font-medium text-amber-100">{section.title}</h5>
        <p className="whitespace-pre-wrap break-words text-sm leading-7 text-stone-300">{section.explanation}</p>
        <p className="rounded-lg bg-white/5 p-3 text-sm leading-7 text-stone-300"><span className="font-medium">证据边界：</span>{section.boundary}</p>
        {section.materials.length ? <ul className="space-y-2">{section.materials.map(material => <li key={material.id}>
          <details className="rounded-lg border border-white/10 p-3">
            <summary className="cursor-pointer break-words text-sm leading-6">{material.title} · {material.cited ? '末次已引用' : '结案时已取得，末次未引用'}</summary>
            <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-7 text-stone-400">{material.text}</p>
          </details>
        </li>)}</ul> : <p className="text-sm leading-7 text-stone-400">结案时没有取得这一主题下的相关材料；上述作者解释不计作本局发现。</p>}
      </article>)}
    </section>}
  </section>;
}
