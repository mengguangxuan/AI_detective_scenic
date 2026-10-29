import { useId, type FormEvent } from 'react';
import type { ClueView } from '../../../apis/games/objects/ClueView';
import type { TimeAssessment } from '../../../apis/games/objects/TimeAssessment';
import type { TimeComparisonDraft } from '../objects/TimeComparisonDraft';
import type { ClueId } from '../../../objects/ClueId';

type Props = { clues: readonly ClueView[]; disabled: boolean; completed: boolean; notice: string; draft: TimeComparisonDraft; onDraftChange: (draft: TimeComparisonDraft) => void;
  onCompare: (evidence: readonly ClueId[], minutes: number, assessment: TimeAssessment) => void };
export default function TimeComparisonPanel({ clues, disabled, completed, notice, onCompare, draft, onDraftChange }: Props) {
  const assessmentId = useId();
  const { evidence, time, assessment, attempted } = draft;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || completed || !assessment || !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) return;
    const [hours, minutes] = time.split(':').map(Number);
    if (hours === undefined || minutes === undefined) return;
    onDraftChange({ ...draft, attempted: true });
    onCompare(evidence, hours * 60 + minutes, assessment);
  }
  return <section aria-label="我的时间核对" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <h2 className="text-lg font-medium">我的时间核对</h2>
    {completed ? <p className="mt-3 text-sm text-stone-300">这份核对已存入调查笔记。</p> : <form onSubmit={submit} className="mt-3 space-y-5">
      <p className="text-sm leading-6 text-stone-400">选择用到的材料，记录你换算出的时刻和判断。</p>
      <fieldset disabled={disabled} className="space-y-3"><legend className="mb-3 text-sm text-stone-300">引用材料</legend>
        {clues.length === 0 ? <p className="text-sm text-stone-500">调查取得的材料会出现在这里。</p> : clues.map(clue => <label key={clue.id} className="flex cursor-pointer items-start gap-3 text-sm text-stone-200">
          <input type="checkbox" checked={evidence.includes(clue.id)} onChange={event => onDraftChange({ ...draft, evidence: event.target.checked ? [...evidence, clue.id] : evidence.filter(id => id !== clue.id) })} className="mt-1 accent-amber-200"/>{clue.title}
        </label>)}
      </fieldset>
      <div className="flex flex-wrap items-end gap-4">
        <label className="flex flex-col gap-2 text-sm text-stone-300">换算后的时刻（24小时制）<input type="text" required pattern="([01][0-9]|2[0-3]):[0-5][0-9]" placeholder="HH:mm" value={time} onChange={event => onDraftChange({ ...draft, time: event.target.value })} disabled={disabled} className="w-44 rounded-lg border border-white/25 bg-stone-950/40 px-3 py-2 text-stone-100"/></label>
        <div className="flex flex-col gap-2 text-sm text-stone-300"><label htmlFor={assessmentId}>我的判断</label><select id={assessmentId} required value={assessment} onChange={event => onDraftChange({ ...draft, assessment: event.target.value as TimeAssessment | '' })} disabled={disabled} className="rounded-lg border border-white/25 bg-[#182421] px-3 py-2 text-stone-100"><option value="" disabled>请选择</option><option value="consistent">这些时间能够相互吻合</option><option value="conflict">这些时间有矛盾</option><option value="uncertain">现有材料还无法判断</option></select></div>
        <button type="submit" disabled={disabled} className="rounded-lg bg-amber-200 px-5 py-2 text-sm font-medium text-stone-950 disabled:opacity-40">提交核对</button>
      </div>
      {attempted ? <p role="status" className="text-sm text-amber-100">{notice}</p> : null}
    </form>}
  </section>;
}
