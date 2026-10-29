import { useId, useState } from 'react';
import type { InvestigationSnapshot } from '../../apis/games/objects/InvestigationSnapshot';
import InvestigationLink from '../../app/components/InvestigationLink';

export default function JournalPage({ snapshot }: { snapshot: InvestigationSnapshot }) {
  const [query, setQuery] = useState('');
  const [onlyMaterials, setOnlyMaterials] = useState(false);
  const [newestFirst, setNewestFirst] = useState(false);
  const filterId = useId();
  const search = query.trim().toLocaleLowerCase();
  const titles = new Map(snapshot.clues.map(clue => [clue.id, clue.title]));
  const records = snapshot.journal.filter(record => (!onlyMaterials || record.clueIds.length > 0) &&
    (!search || [record.text, record.storyTimeLabel, ...record.clueIds.map(id => titles.get(id) ?? '')].join('\n').toLocaleLowerCase().includes(search)));
  if (newestFirst) records.reverse();
  return <section aria-labelledby="journal-page-title" className="space-y-5">
    <div><h2 id="journal-page-title" className="text-xl font-medium">调查日志</h2><p className="mt-2 text-sm leading-6 text-stone-400">回看你的调查顺序、执行过的操作和当时新取得的材料。</p></div>
    <div className="rounded-2xl border border-white/10 bg-[#182421] p-5">
      <div className="grid items-end gap-4 sm:grid-cols-[minmax(0,1fr)_auto]">
        <div><label htmlFor={`${filterId}-query`} className="text-sm text-stone-300">搜索记录</label><input id={`${filterId}-query`} type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="输入材料名称或记录内容" className="mt-2 block w-full min-w-0 rounded-lg border border-white/20 bg-[#101918] px-3 py-2.5 text-sm placeholder:text-stone-500"/></div>
        <div><label htmlFor={`${filterId}-order`} className="text-sm text-stone-300">排列顺序</label><select id={`${filterId}-order`} value={newestFirst ? 'newest' : 'oldest'} onChange={event => setNewestFirst(event.target.value === 'newest')} className="mt-2 block w-full rounded-lg border border-white/20 bg-[#101918] px-3 py-2.5 text-sm"><option value="oldest">最早的在前</option><option value="newest">最新的在前</option></select></div>
      </div>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-4"><label className="flex items-center gap-2 text-sm text-stone-300"><input type="checkbox" checked={onlyMaterials} onChange={event => setOnlyMaterials(event.target.checked)} className="accent-amber-200"/>只看有新材料的记录</label><p role="status" className="text-xs text-stone-400">显示 {records.length} / {snapshot.journal.length} 条记录</p></div>
    </div>
    <section aria-label="调查记录" className="rounded-2xl border border-white/10 bg-[#182421] p-5 sm:p-7">
      {snapshot.journal.length === 0 ? <div><p className="text-sm leading-7 text-stone-400">还没有调查记录。检查物品或与人物交谈后，保存的结果会出现在这里。</p><InvestigationLink href="/scene" className="mt-4 inline-block text-sm text-amber-200 underline underline-offset-4">前往现场探索</InvestigationLink></div>
        : records.length === 0 ? <p className="text-sm leading-7 text-stone-400">没有符合条件的记录。可以更换搜索词，或取消材料筛选。</p>
          : <ol className="space-y-6">{records.map(record => <li key={record.id} className="flex min-w-0 gap-4 border-b border-white/10 pb-6 last:border-0 last:pb-0"><span className="pt-0.5 text-sm tabular-nums text-amber-200" aria-label={`第${record.sequence}条记录`}>{String(record.sequence).padStart(2, '0')}</span><article className="min-w-0 flex-1"><p className="whitespace-pre-wrap break-words text-sm leading-7 text-stone-200">{record.text}</p>{record.clueIds.length ? <p className="mt-3 text-xs leading-6 text-amber-100">新记录材料：{record.clueIds.map(id => titles.get(id) ?? '已记录材料').join('、')}</p> : null}<p className="mt-2 text-xs text-stone-500">{record.storyTimeLabel}</p></article></li>)}</ol>}
    </section>
    {snapshot.clues.length ? <InvestigationLink href="/clues" className="inline-block text-sm text-amber-200 underline underline-offset-4">前往线索整理查看全部材料</InvestigationLink> : null}
  </section>;
}
