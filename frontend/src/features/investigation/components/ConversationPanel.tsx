import type { WitnessId } from '../../../objects/WitnessId';
import { useEffect, useId, useState, type FormEvent } from 'react';
import type { WitnessView } from '../../../apis/games/objects/WitnessView';
import type { ClueView } from '../../../apis/games/objects/ClueView';
import type { InterviewTurnView } from '../../../apis/games/objects/InterviewTurnView';
import type { ClueId } from '../../../objects/ClueId';

type Props = { witness: WitnessView; clues: readonly ClueView[]; turns: readonly InterviewTurnView[]; disabled: boolean;
  onAsk: (witness: WitnessId, question: string, evidence: readonly ClueId[]) => void };
export default function ConversationPanel({ witness, clues, turns, disabled, onAsk }: Props) {
  const questionId = useId();
  const [question, setQuestion] = useState('');
  const [evidence, setEvidence] = useState<readonly ClueId[]>([]);
  // Clear the composer only after the submitted question is actually restored in the server view.
  const [submitted, setSubmitted] = useState<{ text: string; sequence: number } | null>(null);
  useEffect(() => {
    if (submitted && turns.some(t => t.question === submitted.text && t.sequence > submitted.sequence)) {
      setQuestion(current => current.trim() === submitted.text ? '' : current);
      setSubmitted(null);
    }
  }, [turns, submitted]);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || !question.trim() || question.length > 1000) return;
    setSubmitted({ text: question.trim(), sequence: turns.at(-1)?.sequence ?? 0 });
    onAsk(witness.id, question, evidence);
  }
  return <section aria-label="自由问询" className="rounded-2xl border border-white/10 bg-[#182421] p-5">
    <h3 className="text-lg font-medium">向{witness.name}提问</h3>
    <p className="mt-2 text-sm leading-6 text-stone-400">{witness.background}</p>
    <form onSubmit={submit} className="mt-4 space-y-4">
      <div className="text-sm text-stone-300"><label htmlFor={questionId}>你的问题</label><textarea id={questionId} value={question} onChange={event => setQuestion(event.target.value)} required maxLength={1000} disabled={disabled} rows={3}
        placeholder="输入你想问的问题…" className="mt-2 block w-full rounded-xl border border-white/25 bg-stone-950/30 p-3 text-stone-100 disabled:opacity-50"/></div>
      <fieldset disabled={disabled}><legend className="text-sm text-stone-300">本次出示的材料（可选）</legend>
        {clues.length === 0 ? <p className="mt-2 text-xs text-stone-500">取得材料后，可在这里选择出示。</p> : <div className="mt-3 flex flex-wrap gap-x-5 gap-y-3">{clues.map(clue => <label key={clue.id} className="flex items-center gap-2 text-sm text-stone-300"><input type="checkbox" checked={evidence.includes(clue.id)} onChange={event => setEvidence(current => event.target.checked ? [...current, clue.id] : current.filter(id => id !== clue.id))} className="accent-amber-200"/>{clue.title}</label>)}</div>}
      </fieldset>
      <button type="submit" disabled={disabled || !question.trim()} className="rounded-lg bg-amber-200 px-5 py-2.5 text-sm font-medium text-stone-950 disabled:opacity-40">{disabled ? '正在处理问询…' : '发送问题'}</button>
    </form>
    <div role="log" aria-label="自由问询记录" aria-live="polite" className="mt-6 space-y-4">
      {turns.map(turn => <article key={turn.requestId} className="border-t border-white/10 pt-4">
        <p className="whitespace-pre-wrap text-sm text-amber-100">你：{turn.question}</p>
        <p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-stone-200">{turn.mode === 'answered' || turn.mode === 'unknown' ? `${witness.name}：${turn.reply}` : turn.reply}</p>
        {turn.shownClues.length ? <p className="mt-2 text-xs text-stone-500">已出示：{turn.shownClues.map(id => clues.find(c => c.id === id)?.title ?? id).join('、')}</p> : null}
      </article>)}
      {turns.length === 0 ? <p className="text-sm text-stone-500">尚未进行自由问询。</p> : null}
    </div>
  </section>;
}
