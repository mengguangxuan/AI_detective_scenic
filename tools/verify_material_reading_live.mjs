// Author regression: reproduces selected-material questions through public game APIs only.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const cache = fileURLToPath(new URL('../frontend/.build-cache/material-reading/', import.meta.url));
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
async function ask(label, question, evidence) {
  const { request, result } = await change('askwitness', { witness: 'redding', question, evidence });
  const turn = state.interviews.at(-1);
  const row = { label, question, selected: evidence.map(id => state.clues.find(c => c.id === id)), mode: turn.mode, reply: turn.reply, acquired: result.acquiredClues };
  rows.push(row); writeFileSync(archive, JSON.stringify(rows, null, 2)); console.log(JSON.stringify(row));
  writeFileSync(cache + 'latest-state.json', JSON.stringify({ cookie, sessionId, state }));
  assert.equal(turn.mode, 'answered', label);
  assert.deepEqual(new Set(turn.shownClues), new Set(evidence));
  assert.deepEqual(await post('askwitness', request), result);
  return turn.reply;
}
if (process.argv.includes('--restore')) {
  const saved = JSON.parse(readFileSync(cache + 'latest-state.json', 'utf8'));
  cookie = saved.cookie; sessionId = saved.sessionId; await refresh(); assert.deepEqual(state, saved.state);
  console.log('Exact selected-material dialogue snapshot restored.');
} else {
  await post('openlocalidentity'); state = await post('openinvestigation', { sessionId, caseId: 'vicarage_study', revision: { value: 23 } });
  await read('route_redding_account'); await read('route_redding_gun');
  await change('executeoperation', { operation: 'open_anne_meeting' }); await read('route_anne_time'); await read('route_anne_gun');
  const anne = ['clue_anne_time', 'clue_anne_gun'];
  const firstAnne = await ask('anne-generic', '安妮也自首了，你有什么要补充的吗', anne);
  assert(/抽屉|梳妆台|六点|6[:：]|18[:：]|她说.{0,20}开.{0,3}枪|她.{0,6}声称.{0,20}开.{0,3}枪/.test(firstAnne), 'The first answer must acknowledge a concrete detail rather than ask what Anne said');
  const exactAnne = await ask('anne-reading', '先不谈你是否相信，请说这两份陈述各自记了什么，再说你是否认同。', anne);
  assert(/抽屉|梳妆台/.test(exactAnne), 'The selected gun statement must be readable without repeating its contents in the question');
  assert(/六点|6[:：]|18[:：]/.test(exactAnne), 'The selected account time must be readable');
  const recalled = await ask('anne-recall', '刚才给你的两份材料记了什么，还记得吗？', []);
  assert(/抽屉|梳妆台/.test(recalled), 'Previously shown statements remain known when not selected again');
  await read('route_police_medical');
  const relative = await ask('medical-relative', '医生对死亡时间的意见是什么？先说这份记录写了什么。', ['clue_medical_time']);
  assert(/半.{0,2}小时|半个钟|三十.{0,2}分|30.{0,2}分/.test(relative), 'A relative time opinion is still an opinion, not missing information');
  await change('requestexternalcheck', { check: 'medical_timing' }); await change('advancetime', { minutes: 15 });
  const medical = ['clue_medical_time', 'clue_medical_cause', 'clue_medical_visit_record'];
  const firstMedical = await ask('medical-generic', '医生诊断的死亡时间和你的供述不符，你有什么要补充的', medical);
  assert(/半.{0,2}小时|半个钟|三十.{0,2}分|30.{0,2}分|六点半|七点|19[:：]/.test(firstMedical), 'A generic challenge must still acknowledge the supplied medical record');
  const exactMedical = await ask('medical-reading', '先只谈你刚收到的这几份医学材料：医生说的相对死亡时长是什么，到场参照又是什么？这些是精确时间吗？', medical);
  assert(/半.{0,2}小时|半个钟|三十.{0,2}分|30.{0,2}分/.test(exactMedical));
  assert(/七点|7[:：]|19[:：]/.test(exactMedical), 'The actual arrival reference must be readable');
  console.log('Selected text, prior knowledge and medical reference checks passed. Review all replies for character and uncertainty.');
}
