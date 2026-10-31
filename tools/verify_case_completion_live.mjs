// Author-side acceptance runner: includes spoilers. Uses only the local game's public APIs.
// Model credentials remain in the backend. Run explicitly with a configured real provider.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const cache = fileURLToPath(new URL('../frontend/.build-cache/', import.meta.url));
mkdirSync(cache, { recursive: true });
const origin = 'http://127.0.0.1:5175';
const key = 'detective-scenic:server-session:v6';
const base = 'http://127.0.0.1:8080/api/';
class Investigation {
  cookie = ''; sessionId = randomUUID(); state = null;
  async post(name, data = {}) {
    const r = await fetch(base + name, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Detective-Client': 'web', ...(this.cookie ? { Cookie: this.cookie } : {}) }, body: JSON.stringify(data), signal: AbortSignal.timeout(75000) });
    const cookie = r.headers.get('set-cookie'); if (cookie) this.cookie = cookie.split(';')[0];
    assert.equal(r.status, 200, `${name}: local API HTTP ${r.status}`);
    return r.json();
  }
  async refresh() { this.state = await this.post('getinvestigationsnapshot', { sessionId: this.sessionId }); return this.state; }
  async start() { await this.post('openlocalidentity'); this.state = await this.post('openstudy', { sessionId: this.sessionId }); assert.equal(this.state.caseRevision.value, 19); }
  async change(name, payload) {
    const request = { sessionId: this.sessionId, requestId: randomUUID(), expectedSequence: this.state.sequence, ...payload };
    const receipt = await this.post(name, request); await this.refresh(); return { request, receipt };
  }
  async read(route) {
    const point = this.state.points.find(p => p.routes.some(r => r.id === route));
    assert(point, `Actual route must be available: ${route}`);
    await this.change('investigate', { point: point.id, route });
  }
  async operate(operation) {
    assert(this.state.points.some(p => p.operations.some(o => o.id === operation)), `Actual operation must be available: ${operation}`);
    await this.change('executeoperation', { operation });
  }
  async check(check) {
    assert(this.state.externalChecks.some(c => c.id === check && c.available), `Actual check must be available: ${check}`);
    await this.change('requestexternalcheck', { check });
  }
  async wait(minutes) { await this.change('advancetime', { minutes }); }
  async ask(question, clue) {
    await this.change('askwitness', { witness: 'clement', question, evidence: [] });
    assert.equal(this.state.interviews.at(-1).mode.toLowerCase(), 'answered');
    assert(this.state.clues.some(c => c.id === clue), `Natural question did not acquire ${clue}`);
  }
  save(name) {
    writeFileSync(cache + name + '-snapshot.json', JSON.stringify(this.state, null, 2));
    const split = this.cookie.indexOf('=');
    const storage = { cookies: [{ name: this.cookie.slice(0, split), value: this.cookie.slice(split + 1), domain: '127.0.0.1', path: '/', expires: -1, httpOnly: true, secure: false, sameSite: 'Lax' }], origins: [{ origin, localStorage: [{ name: key, value: JSON.stringify({ version: 1, sessionId: this.sessionId, creationPending: false, pending: null }) }] }] };
    writeFileSync(cache + name + '-state.json', JSON.stringify(storage));
  }
}
async function collect(game, natural = false) {
  await game.start();
  if (natural) {
    await game.ask('案发之前，你亲眼见过安妮和雷丁怎样相处？他们有没有分别找你谈过这段关系？', 'clue_clement_relationship');
    await game.ask('先别说你晚上回家遇见雷丁。周四你接到探病电话以前，他有没有来过书房？当时说了什么？', 'clue_clement_early_visit');
  } else {
    await game.read('route_clement_relationship'); await game.read('route_clement_early_visit');
  }
  for (const route of ['route_desk', 'route_clock', 'route_paper', 'route_habit', 'route_arrival', 'route_arrival_basis', 'route_discovery', 'route_call', 'route_redding_account', 'route_redding_gun', 'route_redding_clock', 'route_police_medical']) await game.read(route);
  await game.check('medical_timing'); await game.check('gun_verification'); await game.check('paper_inspection');
  await game.operate('open_anne_meeting');
  for (const route of ['route_anne_statement', 'route_anne_time', 'route_anne_gun', 'route_anne_redding']) await game.read(route);
  await game.operate('open_valet_meeting'); await game.read('route_valet_gun'); await game.operate('open_movement_meetings');
  for (const route of ['route_stone_walk', 'route_stone_inn', 'route_marple_visitors', 'route_marple_anne_reply', 'route_marple_sound', 'route_hartnell_visit', 'route_mary_sound', 'route_mary_fish_time']) await game.read(route);
  await game.check('paper_handwriting');
  for (const route of ['route_redding_time_challenge', 'route_redding_anne_reply']) await game.read(route);
  await game.operate('begin_redding_followup');
  for (const route of ['route_redding_revised', 'route_redding_gun_revised', 'route_redding_scene_revised', 'route_anne_gun_challenge', 'route_anne_redding_challenge']) await game.read(route);
  await game.operate('begin_anne_followup'); await game.read('route_anne_revised'); await game.read('route_anne_visit_revised');
  await game.read('route_call_basis'); await game.check('call_origin');
  await game.read('route_police_sound_check'); await game.check('woodland_search');
  await game.operate('lift_planter'); await game.read('route_planter_interior'); await game.operate('replace_planter');
  await game.wait(15); await game.check('crystal_opinion'); await game.check('call_lodge_inspection');
  await game.wait(15);
}
// Authored hypotheses explicitly distinguish observed material from unverified reconstruction.
const correct = '我认为安妮与雷丁共谋：安妮实际射击，雷丁负责准备和掩饰。牧师亲见亲吻并听两人谈关系、安妮婚姻痛苦及雷丁无钱带她走，支持摆脱上校的共同动机，但不能单凭私情定罪。牧师被假电话引开，追查指向北门房，支持有意腾空寓所，尚不能单凭地点认定来电者。我推测雷丁承担调离安排。牧师约15:45回家前雷丁已在书房等候，说明他较早有接触现场的机会；结合安妮未带手袋、外盆存在空间，预先留下武器供安妮取用是合理假说，而非已发现藏枪痕迹。马普尔看见安妮约18:16—17转过屋角，很快去画室，她看不到书房内部；这段短暂空隙仍可能发生射击。后来两人公开同行不能证明此前无人作案。座钟18:22按平时快十五分换算成18:07，与教堂钟确定的上校18:15到达冲突；雷丁承认动钟，纸上18:20墨色不同、正文并非上校笔迹，支持现场时间被布置，不能当死亡时刻。雷丁后来再入书房并持枪自首，两人首供具体枪源与时间受到独立材料质疑，补供改为互相保护；可解释为配合误导，不能因此判两人无辜。安妮转角时未听见枪声，后来树林的声音也不必是致命枪击；晶体被初步认为可能是可爆物，支持另行制造声响掩护时间的假说，未化验证实。枪声可能被抑制，具体办法和装置本局尚无检验，不能声称找到消声器。综合这些材料，安妮较早动手、雷丁随后处理枪和伪造纸钟能解释矛盾；同口径及认罪本身都不足以单独证明射手身份。';
const paraphrase = '我的解释先从时刻说起：上校到达有教堂报时作依据，不能接受把座钟机械减一刻得出的18:07；纸上18:20也靠不住，因为数字墨色与正文不同，正文笔迹又不属于死者，加上雷丁承认碰过钟，应考虑有人有意留下错误时间。谁有机会？马普尔只见安妮绕过屋角再迅速走向画室，看不到屋里，稍后的共同露面覆盖不了这一小段。我的判断是安妮趁这段空隙开枪，劳伦斯·雷丁协助，而非雷丁在其自报的18:45独自杀人。他后来进入书房、拿走枪，再交枪认罪；前后口供与安妮的认罪和撤回可以构成共同误导调查的策略，而不是互证无罪。安妮没拿手袋仍可能取得屋内预放的枪：牧师午后回来前雷丁已在书房，盆栽内外有间隙，提示准备的机会与可能位置；这些不等于牧师目睹藏枪或外盆发现枪械痕迹。假病情电话把牧师调离，北门房是接出地点，可能也是雷丁的准备工作，地点记录本身没有认出打电话的人。关系方面，牧师看见两人亲吻，听过安妮婚姻痛苦及雷丁无钱带她走的谈话，支持两人想摆脱上校的动机；恋情不单独证明杀人。树林响声发生得更晚，不能未经证明就用它给两人建立不在场；现场晶体的外观意见提示另造声响的可能，与较早未听见枪声可以并存。我将致命枪声受抑制和后来人为造响作为解释，尚不能证明具体器具、晶体身份与机械结构。综合人物、时间和伪造现场，二人分工合作比两人轮流声称独自行凶更能解释现有材料，我不把任何一份供词或口径相合当成完整证据。';
const results = [];
const reportName = 'completion-live-' + new Date().toISOString().replace(/[:.]/g, '-') + '-report.json';
async function assess(game, label, explanation, expected) {
  const evidence = game.state.clues.map(c => c.id).filter(id => !id.startsWith('clue_police_'));
  assert(evidence.length <= 50);
  const before = game.state.reasoning.attempts.length;
  const { request, receipt } = await game.change('evaluatereasoning', { suspects: ['anne', 'redding'], evidence, explanation });
  const attempt = game.state.reasoning.attempts.at(-1);
  const row = { label, requestId: request.requestId, sessionId: game.sessionId, explanation, evidence, status: attempt.status, checks: attempt.checks, feedback: attempt.feedback, endingHidden: game.state.reasoning.ending === null };
  results.push(row); writeFileSync(cache + 'completion-live-report.json', JSON.stringify(results, null, 2));
  writeFileSync(cache + reportName, JSON.stringify(results, null, 2));
  console.log(JSON.stringify({ label, status: row.status, checks: row.checks, feedback: row.feedback, endingHidden: row.endingHidden }));
  game.save(label === 'same_meaning_different_order' ? 'completion-live-paraphrase' : 'completion-live-open');
  assert.equal(attempt.status, expected, label); assert(row.endingHidden);
  if (expected === 'Established') assert(row.feedback.includes('结案并查看复盘') && !/化验|基本可以|最需要核实/.test(row.feedback), 'An established assessment must not add further tasks');
  const repeated = await game.post('evaluatereasoning', request); assert.deepEqual(repeated, receipt);
  await game.refresh(); assert.equal(game.state.reasoning.attempts.length, before + 1);
}
async function restore() {
  for (const name of ['completion-live-solved', 'completion-live-paraphrase']) {
    const storage = JSON.parse(readFileSync(cache + name + '-state.json', 'utf8'));
    const game = new Investigation(); game.cookie = storage.cookies.map(c => c.name + '=' + c.value).join('; ');
    game.sessionId = JSON.parse(storage.origins[0].localStorage[0].value).sessionId;
    assert.deepEqual(await game.refresh(), JSON.parse(readFileSync(cache + name + '-snapshot.json', 'utf8')));
    console.log(`${name}: exact snapshot restored`);
  }
}
if (process.argv.includes('--restore')) await restore();
else if (process.argv.includes('--paraphrase')) {
  const storage = JSON.parse(readFileSync(cache + 'completion-live-paraphrase-state.json', 'utf8'));
  const game = new Investigation(); game.cookie = storage.cookies.map(c => c.name + '=' + c.value).join('; ');
  game.sessionId = JSON.parse(storage.origins[0].localStorage[0].value).sessionId; await game.refresh();
  await assess(game, 'same_meaning_different_order', paraphrase, 'Established'); game.save('completion-live-paraphrase');
}
else {
  const first = new Investigation();
  if (process.argv.includes('--resume')) {
    const storage = JSON.parse(readFileSync(cache + 'completion-live-open-state.json', 'utf8'));
    first.cookie = storage.cookies.map(c => c.name + '=' + c.value).join('; ');
    first.sessionId = JSON.parse(storage.origins[0].localStorage[0].value).sessionId; await first.refresh();
    assert.equal(first.state.caseRevision.value, 19); assert.equal(first.state.reasoning.ending, null);
  } else await collect(first, true);
  await assess(first, 'names_and_motive_only', '我选择安妮和雷丁，因为他们相爱、想摆脱上校。其他手法、时刻和材料间的联系我暂时解释不出来，先凭这个就算破案吧。', 'Revise');
  await assess(first, 'wrong_shooter_and_time', '两人相爱想摆脱上校，所以一定是雷丁在18:45独自开枪，安妮只在之后帮他逃走。树林的响声就是这一枪，座钟和纸上时间都完全准确。因为两人都认罪，所以没有必要解释任何矛盾，也不必考虑更早的作案机会。', 'Revise');
  await assess(first, 'supported_reconstruction', correct, 'Established'); first.save('completion-live-open');
  await first.change('revealreasoning', { abandon: false }); assert.equal(first.state.reasoning.ending.mode, 'Solved'); first.save('completion-live-solved');
  const second = new Investigation(); await collect(second);
  await assess(second, 'same_meaning_different_order', paraphrase, 'Established'); second.save('completion-live-paraphrase');
  console.log('Live completion acceptance passed; original requests, separate ending and paraphrase checked.');
}
