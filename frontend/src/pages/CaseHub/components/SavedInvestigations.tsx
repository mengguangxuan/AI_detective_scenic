import type { SessionId } from '../../../objects/SessionId';
import type { SavedInvestigationView } from '../../../apis/games/objects/SavedInvestigationView';
import { savedInvestigationLabels } from '../../../apis/games/objects/SavedInvestigationView';

type Props = { investigations: readonly SavedInvestigationView[]; currentId: SessionId | null; blocked: boolean; busy: boolean; onResume: (id: SessionId) => void };
export default function SavedInvestigations({ investigations, currentId, blocked, busy, onResume }: Props) {
  return <section id="my-investigations" aria-labelledby="saved-title"><h2 id="saved-title" className="text-xl font-medium">我的调查</h2>
    <p className="mt-2 text-sm leading-7 text-stone-400">这里显示当前浏览器身份在服务器上保存的调查。每次新建都会保留原进度；切换调查前，请先提交或留存尚未发送的文字。</p>
    {investigations.length ? <ol className="mt-5 space-y-4">{investigations.map(item => <li key={item.sessionId} className="flex flex-wrap items-center justify-between gap-5 rounded-xl border border-white/10 bg-[#182421] p-5">
      <div className="min-w-0"><h3 className="font-medium">{item.title}</h3><p className="mt-2 text-sm text-amber-100">{savedInvestigationLabels[item.status]}{item.sessionId === currentId ? ' · 当前调查' : ''}</p>
        <p className="mt-2 text-xs leading-6 text-stone-400">修订{item.revision} · {item.sequence}条记录 · {item.updatedAtMillis === null ? '历史存档，活动时间未记录' : `最后活动：${new Date(item.updatedAtMillis).toLocaleString('zh-CN')}`}</p></div>
      <button type="button" disabled={busy || (blocked && item.sessionId !== currentId)} onClick={() => onResume(item.sessionId)} className="rounded-lg border border-amber-200/60 px-5 py-2.5 text-sm text-amber-100 disabled:opacity-40">{item.status === 'Investigating' || item.status === 'Established' ? '继续调查' : '回到调查与复盘'}</button>
    </li>)}</ol> : <p className="mt-5 rounded-xl border border-white/10 p-5 text-sm text-stone-400">还没有保存的调查。先选择一个案件开始吧。</p>}
  </section>;
}
