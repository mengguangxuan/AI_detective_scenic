// Real-provider continuity review; only public game APIs, no credentials or private prompts.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const cache = fileURLToPath(new URL('../frontend/.build-cache/dialogue-continuity/', import.meta.url));
mkdirSync(cache, { recursive: true });
const archive = cache + new Date().toISOString().replace(/[:.]/g, '-') + '-report.json';
const rows = [];
let cookie = '', state;
let sessionId = randomUUID();
async function post(name, data = {}) {
  const response = await fetch('http://127.0.0.1:8080/api/' + name, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Detective-Client': 'web', ...(cookie ? { Cookie: cookie } : {}) }, body: JSON.stringify(data), signal: AbortSignal.timeout(75000) });
  const received = response.headers.get('set-cookie'); if (received) cookie = received.split(';')[0];
  assert.equal(response.status, 200, `${name}: HTTP ${response.status}`); return response.json();
}
async function refresh() { state = await post('getinvestigationsnapshot', { sessionId }); }
async function change(name, data) {
  const request = { sessionId, requestId: randomUUID(), expectedSequence: state.sequence, ...data };
  const result = await post(name, request); await refresh(); return { request, result };
}
async function read(route) {
  const point = state.points.find(p => p.routes.some(r => r.id === route)); assert(point, route);
  await change('investigate', { point: point.id, route });
}
async function ask(label, question, evidence = [], witness = 'redding') {
  const { request, result } = await change('askwitness', { witness, question, evidence });
  const turn = state.interviews.at(-1);
  const row = { label, witness, question, selected: evidence.map(id => state.clues.find(c => c.id === id)), mode: turn.mode, reply: turn.reply, acquired: result.acquiredClues };
  rows.push(row); writeFileSync(archive, JSON.stringify(rows, null, 2)); console.log(JSON.stringify(row));
  const saved = JSON.stringify({ cookie, sessionId, state });
  writeFileSync(cache + 'latest-state.json', saved);
  writeFileSync(archive.replace('-report.json', '-state.json'), saved);
  assert.equal(turn.mode, 'answered', label);
  assert.deepEqual(new Set(turn.shownClues), new Set(evidence));
  assert.deepEqual(await post('askwitness', request), result);
  return turn.reply;
}
if (process.argv.includes('--restore')) {
  const saved = JSON.parse(readFileSync(cache + 'latest-state.json', 'utf8'));
  cookie = saved.cookie; sessionId = saved.sessionId; await refresh(); assert.deepEqual(state, saved.state);
  console.log('Exact continuity dialogue snapshot restored.');
} else {
  await post('openlocalidentity'); state = await post('openinvestigation', { sessionId, caseId: 'vicarage_study', revision: { value: 23 } });
  await read('route_redding_account'); await read('route_redding_gun');
  await change('executeoperation', { operation: 'open_anne_meeting' }); await read('route_anne_time'); await read('route_anne_gun');
  const anne = ['clue_anne_time', 'clue_anne_gun'];
  await ask('redding-counterquestion', '先不谈改口。你最想向我确认哪一件事？一次只问一个问题。', anne);
  await ask('redding-answer', '我不知道哪份口供可信，警方也并没有向我作出保证。');
  await ask('redding-short-confirmation', '对，就是我刚才说的意思。');
  await ask('redding-why', '为什么？');
  await ask('redding-reference', '那支枪呢？');
  await ask('redding-clarification', '我指的是你原先声称随身带着的那支，它从哪里来的？');
  const job = await ask('redding-switch-topic', '先换个话题：你平时做什么工作？');
  assert(/画家|画画|绘画|画师/.test(job), 'An explicit topic switch should be answered');
  await ask('mary-time', '你当时在什么地方？', [], 'mary');
  await ask('mary-clarification', '我指的是周四傍晚，你听到书房声音的时候。', [], 'mary');
  await ask('mary-later', '那后来呢？', [], 'mary');
  await ask('mary-why', '为什么？', [], 'mary');
  const back = await ask('redding-return', '我们接着刚才的话，你最后向我说明的是什么？');
  assert(/画家|画画|绘画|画师/.test(back), 'Returning to Redding should resume his own last topic, not Mary dialogue');
  console.log('Protocol, saved receipts, explicit topic switch and witness return checks passed. Short-reference semantics still require review; no blanket quality pass.');
}
