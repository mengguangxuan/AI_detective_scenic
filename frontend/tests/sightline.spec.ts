import { writeFileSync } from 'node:fs';
import { test, expect, type Page } from './support/investigation-test';
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
  await page.goto('/police');
  await expect(page.getByRole('heading', { name: '警方交流', exact: true })).toBeVisible();
  expect((await snapshot(page)).caseRevision.value).toBe(21);
  await expect(page.getByRole('button', { name: '联系阿彻太太，核实雷丁住处的情况', exact: true })).toHaveCount(0);
  await read(page, 'redding', 'redding_account'); await read(page, 'redding', 'redding_gun');
  await read(page, 'police', 'police_medical');
  await command(page, 'requestexternalcheck', { check: 'medical_timing' });
  await command(page, 'requestexternalcheck', { check: 'gun_verification' });
  await operation(page, 'open_anne_meeting');
  for (const name of ['anne_statement', 'anne_time', 'anne_gun']) await read(page, 'anne', name);
  await operation(page, 'open_valet_meeting'); await read(page, 'valet', 'valet_gun');
  await operation(page, 'open_movement_meetings'); await read(page, 'stone', 'stone_inn');
  await read(page, 'marple', 'marple_visitors'); await read(page, 'hartnell', 'hartnell_visit');
  await read(page, 'redding', 'redding_time_challenge'); await read(page, 'redding', 'redding_anne_reply');
  await operation(page, 'begin_redding_followup');
  await read(page, 'redding', 'redding_revised'); await read(page, 'redding', 'redding_gun_revised');
  await read(page, 'anne', 'anne_gun_challenge'); await read(page, 'anne', 'anne_redding_challenge');
  await operation(page, 'begin_anne_followup'); await read(page, 'anne', 'anne_revised');
  await read(page, 'anne', 'anne_visit_revised'); await page.reload();
}

async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
async function send(page: Page, question: string, evidence: string[] = []) {
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  for (const name of evidence) await form.getByRole('checkbox', { name, exact: true }).check();
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  expect((await snapshot(page)).interviews.at(-1).mode).toBe('answered');
  return page.getByRole('log', { name: '自由问询记录', exact: true });
}

test('sightline basis is explicitly submitted, the check finishes, and witnesses receive only the actual report', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses the local protocol fixture.');
  await prepare(page);
  await expect(page.getByRole('button', { name: '申请窗边视线复验', exact: true })).toBeDisabled();
  const original = (await snapshot(page)).clues.filter((c: { id: string }) => ['clue_anne_visit_revised', 'clue_marple_visitors'].includes(c.id));
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '出示安妮与马普尔记录，登记窗边复验依据', exact: true }).click();
  await expect(page.getByRole('button', { name: '申请窗边视线复验', exact: true })).toBeEnabled();
  let state = await snapshot(page);
  expect(state.police.sharedClues).toEqual(expect.arrayContaining(['clue_anne_visit_revised', 'clue_marple_visitors']));
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_sightline_recheck')).toBe(false);
  const requests: string[] = []; let drop = true;
  await page.route('**/api/requestexternalcheck', async route => {
    if (route.request().postDataJSON().check !== 'sightline_recheck') { await route.continue(); return; }
    requests.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  await page.getByRole('button', { name: '申请窗边视线复验', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(requests).toHaveLength(2); expect(requests[0]).toBe(requests[1]);
  await expect(await send(page, '窗边视线复验进展如何？')).toContainText('目前仍在办理');
  await page.getByRole('button', { name: '整理并等待5分钟', exact: true }).click();
  await expect(page.getByRole('article', { name: '窗边视线复验', exact: true })).toContainText('办理中');
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_sightline_recheck')).toBe(false);
  await page.getByRole('button', { name: '整理并等待5分钟', exact: true }).click();
  await expect(page.getByRole('article', { name: '窗边视线复验', exact: true })).toContainText('已完成');
  await expect(await send(page, '窗边视线复验结果是什么？')).toContainText('没有看见坐在书桌前的人');
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与安妮·普罗瑟罗交谈', exact: true }).click();
  await expect(await send(page, '你怎样回应这份视线复验记录？', ['窗边视线复验记录'])).toContainText('我当时就是到窗边看了那么一下');
  await page.getByRole('button', { name: '与马普尔小姐交谈', exact: true }).click();
  const detail = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  await detail.locator('summary').click();
  await detail.getByRole('button', { name: '出示视线复验记录，核对马普尔的观察位置', exact: true }).click();
  await expect(detail).toContainText('那不是我当时站的地方');
  await go(page, '警方交流');
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '出示供述与复验记录，登记视线核查', exact: true }).click();
  state = await snapshot(page);
  for (const id of ['clue_sightline_recheck', 'clue_anne_sightline_reply', 'clue_marple_sightline_reply']) expect(state.clues.some((c: { id: string }) => c.id === id)).toBe(true);
  expect(state.clues.filter((c: { id: string }) => ['clue_anne_visit_revised', 'clue_marple_visitors'].includes(c.id))).toEqual(original);
  expect(state.stage.completed).toBe(false);
  await page.reload(); expect(await snapshot(page)).toEqual(state);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: '.build-cache/sightline-mobile.png', fullPage: true });
  await page.context().storageState({ path: '.build-cache/sightline-state.json' });
  writeFileSync('.build-cache/sightline-snapshot.json', JSON.stringify(state));
});
