import type { TravelChoiceView } from '../../../apis/games/objects/TravelChoiceView';
export default function TravelPanel({ choices, disabled, onTravel }: { choices: readonly TravelChoiceView[]; disabled: boolean; onTravel: (choice: TravelChoiceView) => void }) {
  if (!choices.length) return null;
  return <section aria-label="可行走的路线" className="rounded-xl border border-white/15 bg-[#182421] px-4 py-3">
    <p className="text-xs tracking-wider text-stone-400">选择路线，前往查看</p>
    <div className="mt-3 flex flex-wrap gap-2">{choices.map(choice => <button key={choice.operation} type="button" disabled={disabled} onClick={() => onTravel(choice)}
      className="rounded-lg border border-amber-200/40 px-4 py-2 text-sm text-amber-100 transition hover:bg-amber-100/10 disabled:opacity-40">{choice.label} <span aria-hidden="true">↗</span></button>)}</div>
  </section>;
}
