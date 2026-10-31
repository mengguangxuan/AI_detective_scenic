import { writeFileSync } from 'node:fs';
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
  await page.goto('/police');
  await expect(page.getByRole('heading', { name: '警方交流', exact: true })).toBeVisible();
  expect((await snapshot(page)).caseRevision.value).toBe(20);
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
  await read(page, 'police', 'police_revised_review'); await page.reload();
}

test('Archer contact, actual testimony and police presentation persist through a lost contact reply', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses the local protocol fixture.');
  await prepare(page);
  const payloads: string[] = []; let drop = true;
  await page.route('**/api/executeoperation', async route => {
    if (route.request().postDataJSON().operation !== 'open_archer_meeting') { await route.continue(); return; }
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  await page.getByRole('button', { name: '联系阿彻太太，核实雷丁住处的情况', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(payloads).toHaveLength(2); expect(payloads[0]).toBe(payloads[1]);
  await expect(page.getByRole('region', { name: '已联系的核查对象', exact: true })).toContainText('阿彻太太');
  let state = await snapshot(page);
  expect(state.clues.some((c: { id: string }) => c.id.startsWith('clue_archer_'))).toBe(false);
  await page.getByRole('link', { name: '前往人物问询，核查证言', exact: true }).click();
  await page.getByRole('button', { name: '与阿彻太太交谈', exact: true }).click();
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  await form.getByLabel('你的问题', { exact: true }).fill('最后一次看见枪是什么时候？');
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('十二点三刻');
  state = await snapshot(page);
  expect(state.interviews.at(-1).mode).toBe('answered');
  expect(state.police.sharedClues).not.toContain('clue_archer_gun_memory');
  const detail = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  await detail.locator('summary').click();
  await detail.getByRole('button', { name: '询问：你怎样照料住处，能说明谁来过吗？', exact: true }).click();
  await expect(detail).toContainText('不是对小屋的搜查记录');
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '警方交流', exact: true }).click();
  const police = page.getByRole('region', { name: '自由问询', exact: true });
  for (const name of ['雷丁补充：枪的存放与接触', '阿彻太太的见枪回忆', '阿彻太太的照料范围']) {
    await police.getByRole('checkbox', { name, exact: true }).check();
  }
  await police.getByLabel('你的问题', { exact: true }).fill('请登记阿彻太太与雷丁的材料。');
  await police.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('两份说法');
  state = await snapshot(page);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_archer_review')).toBe(true);
  expect(state.police.sharedClues).toEqual(expect.arrayContaining(['clue_archer_gun_memory', 'clue_archer_household']));
  expect(state.stage.completed).toBe(false);
  await page.reload(); expect(await snapshot(page)).toEqual(state);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: 'test-results/archer-inquiry-mobile.png', fullPage: true });
  await page.context().storageState({ path: '.build-cache/archer-inquiry-state.json' });
  writeFileSync('.build-cache/archer-inquiry-snapshot.json', JSON.stringify(state));
});
