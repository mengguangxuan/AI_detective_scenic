// Real-provider continuity review; only public game APIs, no credentials or private prompts.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const redding = process.argv.includes('--redding');
const cache = fileURLToPath(new URL('../frontend/.build-cache/clarification-focus/' + (redding ? 'redding/' : ''), import.meta.url));
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
if (process.argv.includes('--restore') || process.argv.includes('--continue')) {
  const saved = JSON.parse(readFileSync(cache + 'latest-state.json', 'utf8'));
  cookie = saved.cookie; sessionId = saved.sessionId; await refresh(); assert.deepEqual(state, saved.state);
  console.log('Exact clarification dialogue snapshot restored.');
  if (process.argv.includes('--continue') && redding) {
    await ask('redding-reference-understood', '我指的是你自己带去那支；我不要求你改变原供，只说你是否明白我指哪一支。');
  } else if (process.argv.includes('--continue')) {
    await ask('mary-clarified', '我问的是，牧师为什么叫你去请医生？', [], 'mary');
    await ask('mary-understood', '明白了，那是牧师叫你去的。', [], 'mary');
    await ask('mary-topic-switch', '先换个话题：你在寓所做什么工作？', [], 'mary');
  }
} else {
  await post('openlocalidentity'); state = await post('openinvestigation', { sessionId, caseId: 'vicarage_study', revision: { value: 23 } });
  if (redding) {
    await read('route_redding_account'); await read('route_redding_gun');
    await ask('redding-unclear', '那支枪呢？我还没说明自己指哪一支，你先问清楚。');
    await ask('redding-clarified', '我指的是你最初说随身带来的那支枪。来源是什么？');
  } else {
    await ask('mary-doctor', '周四傍晚后来发生了什么？先说牧师叫你去请医生这件事。', [], 'mary');
    await ask('mary-why', '为什么？', [], 'mary');
    await ask('mary-ambiguous', '你说的那个，为什么会那样？我还没说清具体指哪一件事。', [], 'mary');
    console.log('Preparation saved. Review whether the ambiguous turn asks for clarification; restart backend, then --continue.');
  }
}
