// Real-provider review through public APIs only. Never reads a key or a private dossier.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const cache = fileURLToPath(new URL(process.argv.includes('--ownership') ? '../frontend/.build-cache/semantic-ownership-live/' : '../frontend/.build-cache/semantic-live/', import.meta.url));
mkdirSync(cache, { recursive: true });
const archive = cache + new Date().toISOString().replace(/[:.]/g, '-') + '-state.json';
let cookie = '', sessionId = randomUUID(), state, nextStep = 0, failed = false;
const rows = [];
async function post(name, data = {}) {
  const response = await fetch('http://127.0.0.1:8080/api/' + name, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Detective-Client': 'web', ...(cookie ? { Cookie: cookie } : {}) }, body: JSON.stringify(data), signal: AbortSignal.timeout(100000) });
  const received = response.headers.get('set-cookie'); if (received) cookie = received.split(';')[0];
  assert.equal(response.status, 200, `${name}: HTTP ${response.status}`); return response.json();
}
async function refresh() { state = await post('getinvestigationsnapshot', { sessionId }); }
function save(pending = null) {
  const data = JSON.stringify({ cookie, sessionId, state, rows, pending, nextStep, failed }, null, 2);
  writeFileSync(archive, data); writeFileSync(cache + 'latest-state.json', data);
}
async function change(name, fields) {
  const request = { sessionId, requestId: randomUUID(), expectedSequence: state.sequence, ...fields };
  save({ name, request });
  const receipt = await post(name, request); await refresh(); save(); return { request, receipt };
}
const fullSteps = [
  { name: 'askwitness', label: 'habit-paraphrase', witness: 'clement', question: '书房那座钟平时准不准？你有没有自己的拨钟习惯？', evidence: [], present: 'clue_habit' },
  { route: 'route_clock' }, { route: 'route_clock_presented' },
  { name: 'askwitness', label: 'receipt-through-fixed', witness: 'clement', question: '刚才给你的那份座钟记录写了什么？我问的是那份记录，不是你的习惯。', evidence: [] },
  { name: 'askwitness', label: 'time-not-cause', witness: 'melchett', question: '先只说医生对死亡时间的估计，不要谈死因：是以什么时刻为参照，大约死亡多久？', evidence: [], present: 'clue_medical_time', absent: 'clue_medical_cause' },
  { route: 'route_redding_account' }, { route: 'route_redding_gun' },
  { name: 'askwitness', label: 'ordinary-gun-source', witness: 'redding', question: '你刚才说那支枪带在身上，我只追问它的来源，你能说明自己知道的来历吗？', evidence: [], absent: 'clue_redding_gun_revised' },
  { name: 'executeoperation', operation: 'open_anne_meeting' },
  { route: 'route_anne_statement' }, { route: 'route_anne_time' }, { route: 'route_anne_gun' },
  { name: 'askwitness', label: 'read-without-forced-recantation', witness: 'redding', question: '这两份陈述各记了什么？先说你看到的具体内容，再说你是否相信；我并不要求你撤回自己的供述。', evidence: ['clue_anne_time', 'clue_anne_gun'], absent: 'clue_redding_revised' },
  { name: 'askwitness', label: 'remember-received', witness: 'redding', question: '不重复出示了。刚才两份陈述里，关于从哪里取枪，是怎么说的？', evidence: [] },
  { name: 'requestexternalcheck', check: 'medical_timing' },
  { name: 'executeoperation', operation: 'open_movement_meetings' },
  { route: 'route_stone_walk' }, { route: 'route_stone_inn' }, { name: 'advancetime', minutes: 15 },
  { name: 'askwitness', label: 'substantive-challenge', witness: 'redding', question: '这些实际记录与你独自开枪的原供有冲突。你仍坚持那份供述，还是愿意说明自己进入书房时真正见到了什么？请说清你实际的说法。', evidence: ['clue_anne_statement', 'clue_medical_visit_record', 'clue_stone_walk', 'clue_stone_inn'] },
  { name: 'askwitness', label: 'anne-actual-response', whenClue: 'clue_redding_revised', witness: 'anne', question: '这是雷丁现在的解释和实际核查记录。你读过后，是否仍坚持自己的认罪？请清楚说明你实际经历的窗边和画室经过，以及你当初为什么那样说。', evidence: ['clue_redding_revised', 'clue_medical_visit_record', 'clue_stone_walk', 'clue_stone_inn'] },
  { name: 'askwitness', label: 'police-register-actual-versions', whenClue: 'clue_anne_revised', witness: 'melchett', question: '四份实际陈述都交给你了。请分别保留首供与后来的说法，说明他们现在怎样解释认罪，以及还需要核实的方向；不要把新说法直接当成真相，也不要声称已经完成新检查。', evidence: ['clue_redding_account', 'clue_anne_statement', 'clue_redding_revised', 'clue_anne_revised'] },
];
if (process.argv.includes('--followup')) fullSteps.push(
  { name: 'askwitness', label: 'clarify-actual-retraction', witness: 'redding', question: '你刚才说收回独自开枪的原供。请把这次修正和理由再说明白：你进书房时实际看到了什么，为什么拿走枪、为什么认罪？眼前哪些已经收到的记录促使你不再坚持原来的解释？', evidence: ['clue_anne_statement', 'clue_medical_visit_record', 'clue_stone_walk', 'clue_stone_inn'] },
  { name: 'askwitness', label: 'anne-after-clarification', whenClue: 'clue_redding_revised', witness: 'anne', question: '请读雷丁现在的解释和核查记录。你是否仍承认自己开枪？请说出你实际在窗边和画室的经历，以及先前认罪的理由。', evidence: ['clue_redding_revised', 'clue_medical_visit_record', 'clue_stone_walk', 'clue_stone_inn'] },
  { name: 'askwitness', label: 'police-after-clarification', whenClue: 'clue_anne_revised', witness: 'melchett', question: '请分别记录两人的原供和当前说法，说明他们怎样解释认罪，以及还需核实什么。不要把新说法当作客观真相。', evidence: ['clue_redding_account', 'clue_anne_statement', 'clue_redding_revised', 'clue_anne_revised'] },
);
function checkStep(step) {
  if ((step.present && !state.clues.some(c => c.id === step.present)) || (step.absent && state.clues.some(c => c.id === step.absent))) { failed = true; save(); }
  assert(!failed, step.label + ': unexpected registration');
}
const steps = process.argv.includes('--ownership') ? fullSteps.filter(step => ['route_redding_account', 'route_redding_gun'].includes(step.route) || step.label === 'ordinary-gun-source') : fullSteps;
async function capture(step, request, receipt) {
  const turn = state.interviews.at(-1);
  if (!rows.some(row => row.request.requestId === request.requestId)) rows.push({ label: step.label, request, receipt, mode: turn.mode, reply: turn.reply, recognition: turn.recognition });
  console.log(JSON.stringify({ label: step.label, mode: turn.mode, reply: turn.reply, recognition: turn.recognition }));
  if (turn.mode !== 'answered' || !turn.recognition) { failed = true; save(); }
  assert.equal(turn.mode, 'answered'); assert(turn.recognition);
  for (const claim of turn.recognition.claims) {
    assert(turn.reply.includes(claim.text)); assert(state.clues.some(c => c.id === claim.clue && c.text === claim.text));
  }
  assert.deepEqual(await post('askwitness', request), receipt);
}
if (process.argv.includes('--restore')) {
  const saved = JSON.parse(readFileSync(cache + 'latest-state.json', 'utf8'));
  cookie = saved.cookie; sessionId = saved.sessionId; await refresh(); assert.deepEqual(state, saved.state);
  for (const row of saved.rows) assert.deepEqual(await post('askwitness', row.request), row.receipt);
  console.log('Real semantic replies, recognition, full snapshot and original receipts restored exactly.');
} else {
  if (process.argv.includes('--continue')) {
    const saved = JSON.parse(readFileSync(cache + 'latest-state.json', 'utf8'));
    assert(!saved.failed, 'Preserve the failed semantic result for review; do not reroll it.');
    cookie = saved.cookie; sessionId = saved.sessionId; rows.push(...saved.rows); await refresh();
    assert.deepEqual(state, saved.state);
    nextStep = saved.nextStep ?? steps.findIndex(step => step.question === saved.pending?.request.question);
    assert(nextStep >= 0);
    if (saved.pending) {
      const receipt = await post(saved.pending.name, saved.pending.request); await refresh();
      if (saved.pending.name === 'askwitness') await capture(steps[nextStep], saved.pending.request, receipt);
      checkStep(steps[nextStep]); nextStep++; save();
    }
  } else {
    await post('openlocalidentity'); state = await post('openinvestigation', { sessionId, caseId: 'vicarage_study', revision: { value: 26 } }); save();
  }
  while (nextStep < steps.length) {
    const step = steps[nextStep];
    if (step.whenClue && !state.clues.some(c => c.id === step.whenClue)) {
      console.log(JSON.stringify({ label: step.label, skipped: 'Witness has not actually made the prior revised statement.' })); nextStep++; save(); continue;
    }
    const { name, label, present, absent, route, whenClue, ...fields } = step;
    let result;
    if (route) {
      const point = state.points.find(p => p.routes.some(r => r.id === route)); assert(point, route);
      result = await change('investigate', { point: point.id, route });
    } else result = await change(name, fields);
    if (name === 'askwitness') await capture(step, result.request, result.receipt);
    checkStep(step); nextStep++; save();
  }
  console.log('Review actual wording and claim choices; persistence and ID checks alone do not prove semantic accuracy.');
}
