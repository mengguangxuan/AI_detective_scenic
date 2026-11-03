// Public API smoke test with the configured provider; never reads credentials or private prompts.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const directory = fileURLToPath(new URL('../frontend/.build-cache/interview-reliability/', import.meta.url));
mkdirSync(directory, { recursive: true });
const savedFile = directory + 'latest-state.json';
const archiveFile = directory + new Date().toISOString().replace(/[:.]/g, '-') + '-state.json';
let cookie = '', sessionId = randomUUID(), state;
const requests = [];
async function post(name, data = {}) {
  const response = await fetch('http://127.0.0.1:8080/api/' + name, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Detective-Client': 'web', ...(cookie ? { Cookie: cookie } : {}) },
    body: JSON.stringify(data), signal: AbortSignal.timeout(75000),
  });
  const received = response.headers.get('set-cookie');
  if (received) cookie = received.split(';')[0];
  assert.equal(response.status, 200, `${name}: HTTP ${response.status}`);
  return response.json();
}
async function refresh() { state = await post('getinvestigationsnapshot', { sessionId }); }
if (process.argv.includes('--restore')) {
  const saved = JSON.parse(readFileSync(savedFile, 'utf8'));
  cookie = saved.cookie; sessionId = saved.sessionId;
  await refresh(); assert.deepEqual(state, saved.state);
  for (const item of saved.requests) assert.deepEqual(await post('askwitness', item.request), item.receipt);
  await refresh(); assert.deepEqual(state, saved.state);
  console.log('Original interview replies, snapshot and receipts restored exactly.');
} else {
  await post('openlocalidentity');
  state = await post('openinvestigation', { sessionId, caseId: 'vicarage_study' });
  for (const question of ['为什么上校会出现在你的寓所？', '你刚才说的来访，原本准备谈什么？']) {
    const request = { sessionId, requestId: randomUUID(), expectedSequence: state.sequence, witness: 'clement', question, evidence: [] };
    const receipt = await post('askwitness', request);
    requests.push({ request, receipt });
    await refresh();
    // Save even a failed attempt for review; never silently reroll a rejected reply.
    const saved = JSON.stringify({ cookie, sessionId, state, requests }, null, 2);
    writeFileSync(archiveFile, saved); writeFileSync(savedFile, saved);
    const turn = state.interviews.at(-1);
    console.log(JSON.stringify({ question, mode: turn.mode, reply: turn.reply }));
    assert.equal(turn.mode, 'answered');
    assert.deepEqual(await post('askwitness', request), receipt);
  }
  console.log('Review the actual wording for case accuracy; this smoke test verifies acceptance and continuity only.');
}
