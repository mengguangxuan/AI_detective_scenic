import { writeFileSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses the local two-stage model protocol fixture.');
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200); return response.json();
}
async function go(page: Page, name: string) { await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click(); }
async function setup(page: Page) {
  await page.goto('/conclusion');
  await expect(page.getByRole('heading', { name: '推理与复盘', exact: true })).toBeVisible();
  const first = await snapshot(page);
  expect(first.caseRevision.value).toBe(18); expect(first.reasoning.ending).toBeNull();
  expect(JSON.stringify(first)).not.toContain('authorTruth'); expect(JSON.stringify(first)).not.toContain('预藏枪');
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  await page.getByText('固定问答与材料笔记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '询问：你为什么离开寓所？', exact: true }).click();
  await expect(page.getByRole('button', { name: '询问：你为什么离开寓所？', exact: true })).toHaveCount(0);
  await go(page, '推理与复盘');
  const form = page.getByRole('form', { name: '推理评审', exact: true });
  await form.getByRole('checkbox', { name: '玛丽', exact: true }).check();
  await form.getByRole('checkbox', { name: '牧师接到的电话', exact: true }).check();
  return form;
}
async function assess(page: Page, text: string, count: number) {
  const form = page.getByRole('form', { name: '推理评审', exact: true });
  await form.getByLabel('你的推理（人物责任、动机、经过与证据之间的联系）', { exact: true }).fill(text);
  await form.getByRole('button', { name: '提交推理评审', exact: true }).click();
  await expect.poll(async () => (await snapshot(page)).reasoning.attempts.length).toBe(count);
}
async function save(page: Page, name: string) {
  const state = await snapshot(page);
  await page.context().storageState({ path: `.build-cache/${name}-state.json` });
  writeFileSync(`.build-cache/${name}-snapshot.json`, JSON.stringify(state)); return state;
}

test('repeat assessments preserve prior reasoning; lost replies retry once and success requires a separate ending action', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await setup(page);
  const before = await snapshot(page);
  const data = { sessionId: before.sessionId, requestId: 'early', expectedSequence: before.sequence, abandon: false };
  expect((await page.request.post('/api/revealreasoning', { data, headers: { 'X-Detective-Client': 'web' } })).status()).toBe(409);
  const form = page.getByRole('form', { name: '推理评审', exact: true });
  const original = '我认为应先核实牧师为何离开，来电陈述还不能说明完整行动经过。';
  await form.getByLabel('你的推理（人物责任、动机、经过与证据之间的联系）', { exact: true }).fill(original);
  const payloads: string[] = []; let drop = true;
  await page.route('**/api/evaluatereasoning', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  await form.getByRole('button', { name: '提交推理评审', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  const history = page.getByRole('region', { name: '推理评审记录', exact: true });
  await expect(history).toContainText('第1次推理 · 继续推敲');
  await expect(history).toContainText('请说明来电记录');
  expect(payloads).toHaveLength(2); expect(payloads[0]).toBe(payloads[1]);
  const revised = await snapshot(page);
  expect(revised.reasoning.attempts).toHaveLength(1); expect(revised.reasoning.ending).toBeNull();
  expect(revised.conclusion).toBeNull(); expect(JSON.stringify(revised)).not.toContain('作者复盘');
  await expect(page.getByRole('button', { name: '结案并查看复盘', exact: true })).toHaveCount(0);
  await history.getByRole('button', { name: '以第1次推理继续修改', exact: true }).click();
  await expect(form.getByLabel('你的推理（人物责任、动机、经过与证据之间的联系）', { exact: true })).toHaveValue(original);
  await go(page, '人物问询'); await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
  await page.getByText('固定问答与材料笔记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '询问：树林方向那声响是什么时候？', exact: true }).click();
  await expect(page.getByRole('button', { name: '询问：树林方向那声响是什么时候？', exact: true })).toHaveCount(0);
  await go(page, '推理与复盘');
  await form.getByRole('checkbox', { name: '玛丽', exact: true }).uncheck();
  await form.getByRole('checkbox', { name: '安妮·普罗瑟罗', exact: true }).check();
  await form.getByRole('checkbox', { name: '劳伦斯·雷丁', exact: true }).check();
  await assess(page, '协议验收：论证成立。此标记仅用于本机协议夹具，不代表真实语义判分。', 2);
  await expect(history).toContainText('第2次推理 · 核心推理成立');
  const passed = await snapshot(page);
  expect(passed.reasoning.attempts[0]).toEqual(revised.reasoning.attempts[0]);
  expect(passed.reasoning.ending).toBeNull(); expect(JSON.stringify(passed)).not.toContain('作者复盘');
  await page.getByRole('button', { name: '结案并查看复盘', exact: true }).click();
  await expect(page.getByRole('region', { name: '本案收尾与复盘', exact: true })).toContainText('已记录推理成立后的结案');
  const done = await save(page, 'reasoning-solved');
  expect(done.reasoning.ending.mode).toBe('Solved'); expect(done.clues).toEqual(passed.clues);
  expect(done.stage.completed).toBe(false); expect(done.police.sharedClues).toEqual(before.police.sharedClues);
  await page.reload(); expect(await snapshot(page)).toEqual(done);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: '.build-cache/reasoning-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
});

test('provider errors and malformed output do not end the case; explicit abandonment is recorded separately', async ({ page }) => {
  await setup(page);
  await assess(page, '评审接口故障测试：这是一次需要保存原文但不能误判成立的推理提交。', 1);
  await expect(page.getByRole('region', { name: '推理评审记录', exact: true })).toContainText('评审暂不可用');
  await assess(page, '评审非法输出测试：格式不正确时应保留本次原文，继续允许调查与再提交。', 2);
  const state = await snapshot(page);
  expect(state.reasoning.attempts.map((a: { status: string }) => a.status)).toEqual(['Unavailable', 'Rejected']);
  expect(state.reasoning.ending).toBeNull(); expect(JSON.stringify(state)).not.toContain('PRIVATE-REVIEW');
  await page.getByText('提前查看作者解答（含剧透）', { exact: true }).click();
  await expect(page.getByRole('button', { name: '放弃推理并查看解答', exact: true })).toBeDisabled();
  await page.getByRole('checkbox', { name: '我选择放弃本次推理并查看完整解答', exact: true }).check();
  await page.getByRole('button', { name: '放弃推理并查看解答', exact: true }).click();
  await expect(page.getByRole('region', { name: '本案收尾与复盘', exact: true })).toContainText('本次未计为破案');
  expect((await save(page, 'reasoning-revealed')).reasoning.ending.mode).toBe('Revealed');
});

test('unfinished feedback restores without revealing answers; the boundary rejects invented evidence and client-supplied verdicts', async ({ page }) => {
  await setup(page);
  const before = await snapshot(page);
  const data = { sessionId: before.sessionId, requestId: 'boundary', expectedSequence: before.sequence, suspects: ['mary'], evidence: ['clue_crystal_opinion'], explanation: '这份未取得的材料不能用来支撑我的推理，也不能由客户端伪造评审。' };
  const headers = { 'X-Detective-Client': 'web' };
  expect((await page.request.post('/api/evaluatereasoning', { data, headers })).status()).toBe(403);
  expect((await page.request.post('/api/evaluatereasoning', { data: { ...data, evidence: ['clue_call'], verdict: 'Established' }, headers })).status()).toBe(400);
  expect((await page.request.post('/api/submitconclusion', { data: { ...data, evidence: ['clue_call'] }, headers })).status()).toBe(403);
  expect(await snapshot(page)).toEqual(before);
  await assess(page, '请先检验来电陈述和离开寓所的时间关系，我还没有解释全部经过。', 1);
  const state = await save(page, 'reasoning-open');
  expect(state.reasoning.attempts[0].status).toBe('Revise'); expect(state.reasoning.ending).toBeNull();
  await page.reload(); expect(await snapshot(page)).toEqual(state);
  await expect(page.getByRole('form', { name: '推理评审', exact: true })).toBeVisible();
});
