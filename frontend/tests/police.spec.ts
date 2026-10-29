import { test, expect, type Page } from '@playwright/test';
const sessionKey = 'detective-scenic:server-session:v6';
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}
async function snapshot(page: Page) {
  const saved = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!), sessionKey);
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  return response.json();
}

test('police explain the retained scene and disclose the doctor opinions only after inquiry', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/scene');
  await expect(page.getByText('遗体已送交医生检查', { exact: false })).toBeVisible();
  await go(page, '警方交流');
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  const initial = await snapshot(page);
  expect(initial.clues).toHaveLength(0);
  expect(initial.police.sharedClues).toHaveLength(0);
  await expect(page.getByRole('heading', { name: '医生初查：死因', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '询问医生的初查意见', exact: true }).click();
  await expect(page.getByRole('heading', { name: '医生初查：死因', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '向警方了解情况', exact: true })).toContainText('近距离枪击');
  await expect(page.getByRole('region', { name: '向警方了解情况', exact: true })).toContainText('半小时左右');
  await expect(page.getByRole('region', { name: '向警方了解情况', exact: true })).toContainText('没有写下精确的检查时刻');
  await page.getByRole('button', { name: '询问遗体去向与现场保全', exact: true }).click();
  await expect(page.getByRole('heading', { name: '警方说明：现场保全', exact: true })).toBeVisible();
  const saved = await snapshot(page);
  expect(saved.sequence.value).toBe(2);
  expect(saved.clues).toHaveLength(3);
  expect(saved.stage.completed).toBe(false);
  expect(saved.police.exchanges).toHaveLength(0);
  expect(JSON.stringify(saved)).not.toContain('18:07');
  expect(JSON.stringify(saved)).not.toContain('clue_future_report');
  await page.reload();
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await expect(page.getByRole('heading', { name: '医生初查：死亡时间', exact: true })).toBeVisible();
  expect(await snapshot(page)).toEqual(saved);
  await page.screenshot({ path: 'test-results/police-medical.png', fullPage: true });
  await go(page, '线索整理');
  await expect(page.getByRole('heading', { name: '医生初查：死因', exact: true })).toBeVisible();
  await go(page, '调查日志');
  await expect(page.getByRole('region', { name: '调查记录', exact: true })).toContainText('半小时左右');
  expect(errors).toEqual([]);
});

test('shared evidence is saved once despite a lost response; unknown evidence is refused', async ({ page }) => {
  await page.goto('/scene');
  await page.getByText('物品列表（备用入口）', { exact: true }).click();
  await page.locator('details').getByRole('button', { name: '座钟', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '座钟操作', exact: true })).toContainText('18:22');
  await page.keyboard.press('Escape');
  await go(page, '警方交流');
  const payloads: string[] = [];
  let drop = true;
  await page.route('**/api/reportclues', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await page.getByRole('region', { name: '向警方出示材料', exact: true }).getByRole('checkbox', { name: '座钟读数', exact: true }).check();
  await page.getByRole('button', { name: '向警方出示', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(payloads).toHaveLength(2);
  expect(payloads[0]).toBe(payloads[1]);
  const saved = await snapshot(page);
  expect(saved.sequence.value).toBe(2);
  expect(saved.police.sharedClues).toEqual(['clue_clock']);
  expect(saved.police.exchanges).toHaveLength(1);
  expect(saved.clues).toHaveLength(1);
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await expect(page.getByRole('region', { name: '警方交流记录', exact: true })).toContainText('梅尔切特：收到了');
  await expect(page.getByRole('region', { name: '向警方出示材料', exact: true }).getByRole('checkbox', { name: '座钟读数', exact: true })).toHaveCount(0);
  await expect(page.getByRole('region', { name: '向警方出示材料', exact: true })).toContainText('手中的材料都已向警方出示');
  const forbidden = await page.request.post('/api/reportclues', { data: { sessionId: saved.sessionId, requestId: crypto.randomUUID(),
    expectedSequence: { value: 2 }, evidence: ['clue_medical_time'] }, headers: { 'X-Detective-Client': 'web' } });
  expect(forbidden.status()).toBe(403);
  const duplicate = await page.request.post('/api/reportclues', { data: { sessionId: saved.sessionId, requestId: crypto.randomUUID(),
    expectedSequence: { value: 2 }, evidence: ['clue_clock'] }, headers: { 'X-Detective-Client': 'web' } });
  expect(duplicate.status()).toBe(409);
  expect(await snapshot(page)).toEqual(saved);
  await page.screenshot({ path: 'test-results/police-exchange.png', fullPage: true });
  await page.reload();
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await expect(page.getByRole('region', { name: '警方交流记录', exact: true })).toContainText('座钟读数');
  expect(await snapshot(page)).toEqual(saved);
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
  await expect(page.getByLabel('自由问询记录', { exact: true })).not.toContainText('梅尔切特');
  await go(page, '警方交流');
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
