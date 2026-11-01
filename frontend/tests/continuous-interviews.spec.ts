import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const headers = { 'X-Detective-Client': 'web' };
const key = 'detective-scenic:server-session:v6';
async function post(page: Page, name: string, data: object = {}) {
  const response = await page.request.post('/api/' + name, { headers, data });
  expect(response.status()).toBe(200); return response.json();
}
async function start(page: Page) {
  await post(page, 'openlocalidentity'); const sessionId = randomUUID();
  let state = await post(page, 'openinvestigation', { sessionId, caseId: 'vicarage_study', revision: { value: 22 } });
  async function change(name: string, payload: object) {
    await post(page, name, { sessionId, requestId: randomUUID(), expectedSequence: state.sequence, ...payload });
    state = await post(page, 'getinvestigationsnapshot', { sessionId });
  }
  async function read(route: string) {
    state = await post(page, 'getinvestigationsnapshot', { sessionId });
    const point = state.points.find((p: { routes: { id: string }[] }) => p.routes.some(r => r.id === route));
    expect(point).toBeDefined(); await change('investigate', { point: point.id, route });
  }
  await read('route_redding_account'); await change('executeoperation', { operation: 'open_anne_meeting' }); await read('route_anne_statement');
  await page.addInitScript(({ key, sessionId }) => localStorage.setItem(key, JSON.stringify({ version: 1, sessionId, creationPending: false, pending: null })), { key, sessionId });
  return { sessionId, read, snapshot: () => post(page, 'getinvestigationsnapshot', { sessionId }) };
}
test('continuous interview has no phase panel and fixed statements survive refresh', async ({ page }) => {
  const game = await start(page); await game.read('route_redding_revised');
  const before = await game.snapshot(); expect(before.followUps).toEqual([]);
  await page.goto('/interviews'); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await expect(page.getByText('可以直接追问或出示材料。不同时间的说法会分别记录，在线索整理中对照。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '开始补充问询', exact: true })).toHaveCount(0);
  await page.reload(); expect(await game.snapshot()).toEqual(before);
  await page.context().storageState({ path: '.build-cache/continuous-browser-state.json' });
});
test('continuous free response and fixed fallback share the statement history', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Protocol fixture only; real semantics has a separate live runner.');
  const game = await start(page); await page.goto('/interviews');
  await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  const state = await game.snapshot();
  const title = state.clues.find((c: { id: string }) => c.id === 'clue_anne_statement').title;
  await page.getByRole('region', { name: '自由问询', exact: true }).getByRole('checkbox', { name: title, exact: true }).check();
  const payloads: string[] = []; let drop = true;
  await page.route('**/api/askwitness', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.getByLabel('你的问题', { exact: true }).fill('协议连续：雷丁重新说明');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(payloads).toHaveLength(2); expect(payloads[1]).toBe(payloads[0]);
  await expect.poll(async () => (await game.snapshot()).clues.some((c: { id: string }) => c.id === 'clue_redding_revised')).toBe(true);
  await game.read('route_anne_revised');
  const after = await game.snapshot();
  expect(after.clues.some((c: { id: string }) => c.id === 'clue_redding_account')).toBe(true);
  expect(after.clues.some((c: { id: string }) => c.id === 'clue_anne_revised')).toBe(true);
  const statement = after.clues.find((c: { id: string }) => c.id === 'clue_redding_revised');
  expect(after.interviews.some((t: { reply: string }) => t.reply.includes(statement.text))).toBe(true);
  await page.reload(); expect(await game.snapshot()).toEqual(after);
});
