import { test, expect, type Page } from '@playwright/test';
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200); return response.json();
}
async function command(page: Page, endpoint: string, fields: object) {
  const state = await snapshot(page);
  const response = await page.request.post(`/api/${endpoint}`, { data: { sessionId: state.sessionId, requestId: crypto.randomUUID(), expectedSequence: state.sequence, ...fields }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status(), await response.text()).toBe(200);
}
const read = (page: Page, who: string, route: string) => command(page, 'investigate', { point: `point_${who}`, route: `route_${route}` });
const operation = (page: Page, name: string) => command(page, 'executeoperation', { operation: name });
async function prepare(page: Page) {
  await page.goto('/interviews');
  await expect(page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true })).toBeVisible();
  await read(page, 'redding', 'redding_account'); await read(page, 'redding', 'redding_gun');
  await read(page, 'clock', 'clock'); await read(page, 'redding', 'redding_clock');
  await read(page, 'police', 'police_medical');
  await command(page, 'requestexternalcheck', { check: 'medical_timing' });
  await command(page, 'requestexternalcheck', { check: 'gun_verification' });
  await operation(page, 'open_anne_meeting');
  for (const route of ['anne_statement', 'anne_time', 'anne_gun']) await read(page, 'anne', route);
  await operation(page, 'open_valet_meeting'); await read(page, 'valet', 'valet_gun');
  await operation(page, 'open_movement_meetings'); await read(page, 'stone', 'stone_inn');
  await read(page, 'marple', 'marple_visitors'); await read(page, 'hartnell', 'hartnell_visit');
  await page.reload();
  await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
}
async function send(page: Page, question: string, evidence: string[] = []) {
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  for (const name of evidence) await form.getByRole('checkbox', { name, exact: true }).check();
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  const state = await snapshot(page);
  expect(state.interviews.at(-1).mode).toBe('answered');
  return state;
}
async function beginRedding(page: Page) {
  const panel = page.getByRole('region', { name: '补充问询准备', exact: true });
  await expect(panel.getByRole('button', { name: '开始补充问询', exact: true })).toBeDisabled();
  await panel.getByRole('button', { name: '出示行踪与检查材料，追问六点三刻的说法', exact: true }).click();
  await expect(panel).toContainText('最近固定答复');
  await panel.getByRole('button', { name: '出示安妮在本局的认罪陈述', exact: true }).click();
  const begin = panel.getByRole('button', { name: '开始雷丁的补充问询', exact: true });
  await expect(begin).toBeEnabled();
  return { panel, begin };
}

test('supplementary statements require actual challenges, retain both versions and survive lost begin response', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses local protocol fixture.');
  await prepare(page);
  const { begin } = await beginRedding(page);
  const requests: string[] = []; let drop = true;
  await page.route('**/api/executeoperation', async route => {
    if (route.request().postDataJSON().operation !== 'begin_redding_followup') { await route.continue(); return; }
    requests.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  await begin.click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(requests).toHaveLength(2); expect(requests[0]).toBe(requests[1]);
  let state = await snapshot(page);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_redding_revised')).toBe(false);
  await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await send(page, '请重新说明当晚的经过。'); await send(page, '你现在怎样解释枪的保管？');
  await send(page, '你碰过座钟或纸张吗？');
  await page.getByRole('button', { name: '与安妮·普罗瑟罗交谈', exact: true }).click();
  const panel = page.getByRole('region', { name: '补充问询准备', exact: true });
  await panel.getByRole('button', { name: '出示男仆证言与核验，追问枪的来源', exact: true }).click();
  await panel.getByRole('button', { name: '出示雷丁补充供述与行踪，追问当晚经过', exact: true }).click();
  await panel.getByRole('button', { name: '开始安妮的补充问询', exact: true }).click();
  await expect(panel).toContainText('补充问询已开始');
  await send(page, '你现在怎么解释原先的认罪？'); await send(page, '你去过窗边吗？');
  state = await send(page, '枪是你带去的吗？');
  expect(state.stage.completed).toBe(false);
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '警方交流', exact: true }).click();
  await send(page, '请登记这四份供述。', ['雷丁的首份供述', '雷丁的补充供述', '安妮的认罪陈述', '安妮的补充供述']);
  state = await snapshot(page);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_revised_review')).toBe(true);
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '线索整理', exact: true }).click();
  const comparison = page.getByRole('region', { name: '供述版本对照', exact: true });
  await expect(comparison).toContainText('第 1 条记录');
  await expect(comparison).toContainText('首轮记录'); await expect(comparison).toContainText('补充记录');
  await expect(comparison).toContainText('没有碰尸体');
  await page.screenshot({ path: 'test-results/followup-comparison.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: 'test-results/followup-mobile.png', fullPage: true });
  await page.reload(); await expect(comparison).toBeVisible(); expect(await snapshot(page)).toEqual(state);
  await page.context().storageState({ path: '.build-cache/followup-state.json' });
});
