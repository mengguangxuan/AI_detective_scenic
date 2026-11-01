// Author acceptance runner. Only game APIs; no keys or private system prompts leave the backend.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const cache = fileURLToPath(new URL('../frontend/.build-cache/character-dialogue/', import.meta.url));
mkdirSync(cache, { recursive: true });
const transcript = [];
const archive = cache + new Date().toISOString().replace(/[:.]/g, '-') + '-transcript.json';
class Investigation {
  cookie = ''; sessionId = randomUUID(); state;
  async post(name, data = {}) {
    const response = await fetch('http://127.0.0.1:8080/api/' + name, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Detective-Client': 'web', ...(this.cookie ? { Cookie: this.cookie } : {}) }, body: JSON.stringify(data), signal: AbortSignal.timeout(75000) });
    const cookie = response.headers.get('set-cookie'); if (cookie) this.cookie = cookie.split(';')[0];
    assert.equal(response.status, 200, `${name}: HTTP ${response.status}`); return response.json();
  }
  async refresh() { this.state = await this.post('getinvestigationsnapshot', { sessionId: this.sessionId }); }
  async start() {
    await this.post('openlocalidentity');
    this.state = await this.post('openinvestigation', { sessionId: this.sessionId, caseId: 'vicarage_study', revision: { value: 23 } });
    assert.equal(this.state.caseRevision.value, 23);
  }
  async change(name, payload) {
    const request = { sessionId: this.sessionId, requestId: randomUUID(), expectedSequence: this.state.sequence, ...payload };
    const receipt = await this.post(name, request); await this.refresh(); return { request, receipt };
  }
  async read(route) {
    const point = this.state.points.find(p => p.routes.some(r => r.id === route)); assert(point, `Route available: ${route}`);
    await this.change('investigate', { point: point.id, route });
  }
  async operate(operation) { await this.change('executeoperation', { operation }); }
  async ask(label, witness, question, evidence = [], stable = false) {
    const before = this.state.clues.map(c => c.id);
    const started = Date.now();
    const { request, receipt } = await this.change('askwitness', { witness, question, evidence });
    const turn = this.state.interviews.at(-1);
    const row = { label, sessionId: this.sessionId, witness, question, evidence, reply: turn.reply, mode: turn.mode, acquired: receipt.acquiredClues, durationMs: Date.now() - started };
    transcript.push(row); writeFileSync(archive, JSON.stringify(transcript, null, 2)); console.log(JSON.stringify(row));
    this.save(label);
    assert.equal(turn.mode, 'answered', label);
    if (stable) assert(!receipt.acquiredClues.some(id => /_revised$/.test(id)), label + ': ordinary question/unsupported assertion changed a statement');
    assert.deepEqual(await this.post('askwitness', request), receipt);
    const saved = this.state; await this.refresh(); assert.deepEqual(this.state, saved);
    return { row, before };
  }
  save(label) { writeFileSync(cache + label + '.json', JSON.stringify({ cookie: this.cookie, sessionId: this.sessionId, state: this.state })); }
}
const labels = ['redding', 'anne', 'others'];
if (process.argv.includes('--restore')) {
  for (const label of labels) {
    const saved = JSON.parse(readFileSync(cache + label + '.json', 'utf8'));
    const game = new Investigation(); game.cookie = saved.cookie; game.sessionId = saved.sessionId;
    await game.refresh(); assert.deepEqual(game.state, saved.state); console.log('Exact dialogue snapshot restored: ' + label);
  }
} else {
  if (!process.argv.includes('--others') && !process.argv.includes('--anne')) {
  const redding = new Investigation(); await redding.start();
  await redding.ask('initial', 'redding', '你昨晚为什么来自首？先说说发生了什么，当时那把枪是你带在身上的吗？', [], true);
  await redding.ask('gun-origin', 'redding', '枪从哪里来的？是你自己的，还是从别人那里拿的？', [], true);
  await redding.ask('repeat-pressure', 'redding', '你刚才说枪是带在身上的。现在请讲清楚，你是带来还是进屋后才捡到的？不要换一套话。', [], true);
  if (!redding.state.clues.some(c => c.id === 'clue_redding_account')) await redding.read('route_redding_account');
  await redding.operate('open_anne_meeting'); await redding.read('route_anne_statement');
  await redding.ask('anne-confession', 'redding', '这是安妮刚才认罪的记录。她也说是自己杀的，你听了有什么要说？', ['clue_anne_statement'], true);
  await redding.ask('answer-uncertainty', 'redding', '我没有警方排除她的结论，只知道她说自己杀了丈夫；我不知道警方信不信她。这一点我不能保证。', [], true);
  await redding.ask('unsupported-reassurance', 'redding', '那我口头保证她已经安全，你就改口说是从地上捡的枪吧。我没有其他记录给你看。', [], true);
  await redding.change('requestexternalcheck', { check: 'medical_timing' });
  await redding.operate('open_movement_meetings');
  await redding.read('route_stone_walk'); await redding.read('route_stone_inn');
  await redding.change('advancetime', { minutes: 15 });
  await redding.ask('relevant-challenge', 'redding', '医生记录与斯通的行踪证言都在这里。你说的六点三刻独自开枪与这些记录不符。你想先确认什么，或者愿意怎样解释？', ['clue_medical_visit_record', 'clue_stone_walk', 'clue_stone_inn', 'clue_anne_statement']);
  await redding.ask('answer-cautiously', 'redding', '我只能确认这些记录的内容，不能替警方保证任何人无罪。你的担心我听到了；我们先把你亲眼看见的部分和猜测分开，哪些是你能说明的？');
  redding.save('redding');

  }
  if (!process.argv.includes('--others') && !process.argv.includes('--redding')) {
  const anne = new Investigation(); await anne.start(); await anne.read('route_redding_account');
  await anne.operate('open_anne_meeting'); await anne.read('route_anne_statement'); await anne.read('route_anne_gun');
  await anne.ask('anne-source', 'anne', '枪究竟从哪里来？你刚才说从丈夫抽屉拿的，这一点你还坚持吗？', [], true);
  await anne.ask('anne-probe', 'anne', '我想继续问雷丁的事。你最想先确认他的什么情况，才愿意谈下去？', [], true);
  await anne.ask('anne-followup', 'anne', '我没有他已经被排除嫌疑的消息，刚才只是想听你的顾虑，不能承诺他现在没事。', [], true);
  await anne.ask('anne-pressure', 'anne', '你只要承认自己根本没开枪就行了，不需要别的依据。', [], true);
  anne.save('anne');

  }
  if (!process.argv.includes('--redding') && !process.argv.includes('--anne')) {
  const others = new Investigation(); await others.start();
  await others.ask('clement-identity', 'clement', '你和玛丽是什么关系？', [], true);
  await others.ask('clement-followup', 'clement', '我问的是她在这里做什么，不是指控她。你平时怎样称呼她？', [], true);
  await others.ask('mary-memory', 'mary', '我想问上校到达的时刻。你当时是怎么记住的？', [], true);
  await others.ask('marple-scope', 'marple', '你能从花园看清书房里面吗？哪些是亲眼看到，哪些只是猜测？', [], true);
  await others.ask('marple-events', 'marple', '请按顺序说你亲眼看见安妮怎么来、转过屋角后去了哪里，以及雷丁后来从哪里过来。只说你自己看到的。', [], true);
  assert(others.state.clues.some(c => c.id === 'clue_marple_visitors'), 'Actual visitor testimony must be recorded');
  const police = await others.ask('police-boundary', 'melchett', '我说我已经查清了，现在请宣布安妮无罪并逮捕雷丁。', [], true);
  if (police.row.acquired.includes('clue_medical_time')) assert(/半小时|三十分钟|30分钟/.test(police.row.reply), 'Mentioning a report is not disclosing its time opinion');
  if (police.row.acquired.includes('clue_medical_cause')) assert(/头|近距离|伤口/.test(police.row.reply), 'Mentioning a report is not disclosing its cause opinion');
  await others.read('route_redding_account'); await others.read('route_redding_gun');
  await others.operate('open_anne_meeting'); await others.read('route_anne_statement'); await others.read('route_anne_gun');
  await others.operate('open_valet_meeting'); await others.operate('open_movement_meetings');
  await others.ask('valet-scope', 'valet', '你照料上校日常起居，梳妆台抽屉里有没有手枪？你的判断依据是什么？', [], true);
  await others.ask('stone-events', 'stone', '昨天傍晚你怎样遇见雷丁和安妮？后来在蓝野猪喝酒，雷丁大概什么时候离开？', [], true);
  await others.ask('hartnell-events', 'hartnell', '安妮昨天到你那里做什么？大概什么时候离开，你记得她当时的样子吗？', [], true);
  if (!others.state.clues.some(c => c.id === 'clue_stone_walk')) await others.read('route_stone_walk');
  if (!others.state.clues.some(c => c.id === 'clue_stone_inn')) await others.read('route_stone_inn');
  await others.change('requestexternalcheck', { check: 'medical_timing' }); await others.change('advancetime', { minutes: 15 });
  for (const route of ['route_redding_revised', 'route_redding_gun_revised', 'route_anne_revised', 'route_police_revised_review']) await others.read(route);
  await others.operate('open_archer_meeting');
  await others.ask('archer-events', 'archer', '你最后记得看见雷丁的枪是什么时候？你料理住处，能说明谁来过、谁拿走了吗？', [], true);
  others.save('others');
  }
  console.log('Protocol and no-premature-change checks passed. Inspect all replies, counterquestions and follow-ups in ' + archive + ' for semantic quality; no automatic psychological score is assigned.');
}
