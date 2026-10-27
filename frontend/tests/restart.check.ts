import { test, expect } from '@playwright/test';
test('the same browser identity restores persisted data after a backend process restart', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '信件正文' })).toBeVisible();
  await expect(page.getByRole('button', { name: '在场景中选择信件' })).toBeVisible();
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v1')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  const snapshot = await response.json();
  expect(snapshot.sequence.value).toBe(2);
  expect(snapshot.journal).toHaveLength(2);
  expect(snapshot.clues).toHaveLength(1);
});
