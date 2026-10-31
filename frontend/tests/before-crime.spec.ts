import { writeFileSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200); return response.json();
}
test('Clement recollections are actually asked, survive a lost response and remain separate from a murder conclusion', async ({ page }) => {
  await page.goto('/interviews');
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  expect((await snapshot(page)).caseRevision.value).toBe(21);
  expect((await snapshot(page)).clues).toHaveLength(0);
  const detail = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  await detail.locator('summary').click();
  let sent = 0;
  await page.route('**/api/investigate', async route => {
    sent++; const response = await route.fetch();
    if (sent === 1) await route.abort('failed'); else await route.fulfill({ response });
  });
  await detail.getByRole('button', { name: '询问：案发前安妮与雷丁有过什么来往？', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(detail.getByRole('button', { name: '询问：案发前安妮与雷丁有过什么来往？', exact: true })).toHaveCount(0);
  await detail.getByRole('button', { name: '询问：雷丁在那天傍晚之前来过吗？', exact: true }).click();
  await expect(detail.getByRole('button', { name: '询问：雷丁在那天傍晚之前来过吗？', exact: true })).toHaveCount(0);
  const state = await snapshot(page);
  expect(state.sequence.value).toBe(2); expect(state.clues).toHaveLength(2); expect(sent).toBe(3);
  expect(state.clues.find((c: { id: string }) => c.id === 'clue_clement_early_visit').text).toContain('目击范围');
  expect(state.police.sharedClues).toEqual([]);
  await page.reload(); expect(await snapshot(page)).toEqual(state);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.context().storageState({ path: '.build-cache/before-crime-state.json' });
  writeFileSync('.build-cache/before-crime-snapshot.json', JSON.stringify(state));
});
test.describe('actual revision 18 compatibility', () => {
  test.use({ storageState: '.build-cache/pre-before-crime-v18-state.json' });
  test('planter discovery remains in revision 18 without the new recollections', async ({ page }) => {
    await page.goto('/interviews');
    await expect(page.getByRole('status').filter({ hasText: '已恢复原修订的调查' })).toBeVisible();
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    const state = await snapshot(page);
    expect(state.caseRevision.value).toBe(18); expect(state.scene.revision).toBe('5');
    expect(state.clues.some((c: { id: string }) => c.id === 'clue_planter')).toBe(true);
    expect(state.points.flatMap((p: { routes: { id: string }[] }) => p.routes).some((r: { id: string }) => r.id === 'route_clement_relationship')).toBe(false);
    await page.reload(); expect(await snapshot(page)).toEqual(state);
  });
});
