import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
const headers = { 'X-Detective-Client': 'web' };
const key = 'detective-scenic:investigation-session';
async function state(page: Page) {
  const sessionId = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).sessionId, key);
  const response = await page.request.post('/api/getinvestigationsnapshot', { headers, data: { sessionId } });
  expect(response.status()).toBe(200); return response.json();
}
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
test('offers every existing contact without clues and persists early Archer testimony', async ({ page }) => {
  expect((await page.request.post('/api/openlocalidentity', { headers, data: {} })).status()).toBe(200);
  const sessionId = randomUUID();
  expect((await page.request.post('/api/openinvestigation', { headers, data: { sessionId, caseId: 'vicarage_study' } })).status()).toBe(200);
  await page.addInitScript(({ key, sessionId }) => localStorage.setItem(key, JSON.stringify({ version: 1, sessionId, creationPending: false, pending: null })), { key, sessionId });
  await page.goto('/case');
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  await go(page, '警方交流');
  const arrangements = page.getByRole('region', { name: '调查安排', exact: true });
  await expect(arrangements.getByRole('button')).toHaveCount(4);
  expect((await state(page)).clues).toEqual([]);
  const payloads: string[] = []; let drop = true;
  await page.route('**/api/executeoperation', async route => {
    if (route.request().postDataJSON().operation !== 'open_archer_meeting') return route.continue();
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await arrangements.getByRole('button', { name: '联系阿彻太太，安排会面', exact: true }).click();
  await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(arrangements.getByRole('button')).toHaveCount(3);
  expect(payloads).toHaveLength(2); expect(payloads[0]).toBe(payloads[1]);
  for (const name of ['联系斯通和哈特内尔，安排会面', '联系上校的贴身男仆，安排会面', '安排与安妮正式会面']) {
    await arrangements.getByRole('button', { name, exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: '调查结果已保存，当前视图已更新。' })).toBeVisible();
  }
  const contacted = await state(page);
  expect(contacted.clues).toEqual([]); expect(contacted.interviews).toEqual([]);
  for (const id of ['anne', 'valet', 'stone', 'hartnell', 'archer']) expect(contacted.witnesses.some((w: { id: string }) => w.id === id)).toBe(true);
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与阿彻太太交谈', exact: true }).click();
  await expect(page.getByLabel('你的问题', { exact: true })).toBeEnabled();
  await page.getByText('固定问答与材料笔记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '询问：最后一次记得看见枪是什么时候？', exact: true }).click();
  await expect.poll(async () => (await state(page)).clues.some((c: { id: string }) => c.id === 'clue_archer_gun_memory')).toBe(true);
  const before = await state(page);
  await page.reload();
  await page.getByRole('button', { name: '与阿彻太太交谈', exact: true }).click();
  expect(await state(page)).toEqual(before);
  await page.context().storageState({ path: '.build-cache/open-meetings-state.json' });
  writeFileSync('.build-cache/open-meetings-snapshot.json', JSON.stringify(before));
});
