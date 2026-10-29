import { test, expect } from '@playwright/test';
test('same identity restores the completed stage after a backend process restart', async ({ page }) => {
  await page.goto('/clues');
  await expect(page.getByRole('heading', { name: '阶段完成 · 书房初查' })).toBeVisible();
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  const snapshot = await response.json();
  expect(snapshot.sequence.value).toBe(7);
  expect(snapshot.journal).toHaveLength(7);
  expect(snapshot.clues).toHaveLength(7);
  expect(snapshot.stage.completed).toBe(true);
});

test.describe('free conversation recovery', () => {
  test.use({ storageState: '.build-cache/free-interview-browser-state.json' });
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Requires the local model fixture browser state.');
  test('saved free conversations restore after a real backend process restart without calling the model', async ({ page }) => {
    await page.goto('/interviews');
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('拨快一刻钟');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const snapshot = await response.json();
    expect(snapshot.interviews).toHaveLength(2);
    expect(snapshot.clues).toHaveLength(7);
    expect(snapshot.stage.completed).toBe(true);
    await page.screenshot({ path: 'test-results/free-interview-chat.png', fullPage: true });
  });
});
