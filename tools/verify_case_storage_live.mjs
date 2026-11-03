// Deterministic public-API storage acceptance. Run once, restart backend, then --restore.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const folder = fileURLToPath(new URL('../frontend/.build-cache/storage-current/', import.meta.url));
mkdirSync(folder, { recursive: true });
const file = folder + 'investigation.json';
let cookie = '';
async function post(route, data = {}) {
  const response = await fetch('http://127.0.0.1:8080/api/' + route, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Detective-Client': 'web', ...(cookie ? { Cookie: cookie } : {}) },
    body: JSON.stringify(data), signal: AbortSignal.timeout(15000),
  });
  const received = response.headers.get('set-cookie');
  if (received) cookie = received.split(';')[0];
  assert.equal(response.status, 200, route);
  return response.json();
}
if (process.argv.includes('--restore')) {
  const saved = JSON.parse(readFileSync(file, 'utf8')); cookie = saved.cookie;
  assert.deepEqual(await post('getinvestigationsnapshot', { sessionId: saved.sessionId }), saved.snapshot);
  assert.deepEqual(await post('investigate', saved.request), saved.receipt);
  assert.deepEqual(await post('advancetime', saved.waitRequest), saved.waitReceipt);
  assert.deepEqual(await post('getinvestigationsnapshot', { sessionId: saved.sessionId }), saved.snapshot);
  console.log('PASS: full snapshot plus original investigation and wait receipts survived actual process restart.');
} else {
  await post('openlocalidentity');
  const catalog = await post('getcasecatalog');
  assert(catalog.length > 0); assert(!JSON.stringify(catalog).toLowerCase().includes('revision'));
  const caseId = catalog.find(entry => entry.caseId === 'vicarage_study')?.caseId, sessionId = randomUUID();
  assert(caseId, 'The storage regression requires its published first-case fixture');
  const opened = await post('openinvestigation', { sessionId, caseId });
  assert(!JSON.stringify(opened).toLowerCase().includes('revision'));
  assert.deepEqual(await post('openinvestigation', { sessionId, caseId }), opened);
  const point = opened.points.find(p => p.routes.some(r => r.id === 'route_clock'));
  assert(point);
  const request = { sessionId, requestId: randomUUID(), expectedSequence: opened.sequence, point: point.id, route: 'route_clock' };
  const receipt = await post('investigate', request);
  const readSnapshot = await post('getinvestigationsnapshot', { sessionId });
  assert.equal(readSnapshot.sequence.value, opened.sequence.value + 1);
  const waitRequest = { sessionId, requestId: randomUUID(), expectedSequence: readSnapshot.sequence, minutes: 5 };
  const waitReceipt = await post('advancetime', waitRequest);
  assert.deepEqual(await post('advancetime', waitRequest), waitReceipt);
  const snapshot = await post('getinvestigationsnapshot', { sessionId });
  assert.equal(snapshot.sequence.value, readSnapshot.sequence.value + 1);
  assert.equal(snapshot.clock.elapsedSeconds, readSnapshot.clock.elapsedSeconds + 300);
  assert.deepEqual(await post('investigate', request), receipt);
  writeFileSync(file, JSON.stringify({ cookie, sessionId, request, receipt, waitRequest, waitReceipt, snapshot }));
  const split = cookie.indexOf('=');
  writeFileSync(folder + 'browser.json', JSON.stringify({ cookies: [{ name: cookie.slice(0, split), value: cookie.slice(split + 1), domain: '127.0.0.1', path: '/', expires: -1, httpOnly: true, secure: false, sameSite: 'Lax' }], origins: [{ origin: 'http://127.0.0.1:5175', localStorage: [{ name: 'detective-scenic:investigation-session', value: JSON.stringify({ version: 1, sessionId, creationPending: false, pending: null }) }] }] }));
  console.log('PASS: catalogue, opening retry, investigation, wait and both exact receipts; ready for restart.');
}
