import { readFileSync, writeFileSync } from 'node:fs';
import { test, expect } from './support/investigation-test';
test.use({ storageState: '.build-cache/pre-evaluation-v16-state.json' });
test('actual revision 16 keeps its frozen one-shot submission and author review', async ({ page }) => {
  await page.goto('/conclusion');
  await expect(page.getByRole('region', { name: '已保存的推理与复盘', exact: true })).toContainText('本次推理已保存');
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  const state = await response.json();
  expect(state.caseRevision.value).toBe(16); expect(state.reasoning).toBeNull();
  const original = JSON.parse(readFileSync('.build-cache/conclusion-snapshot.json', 'utf8'));
  expect(state).toEqual({ ...original, reasoning: null });
  await expect(page.getByRole('form', { name: '推理评审', exact: true })).toHaveCount(0);
  await page.context().storageState({ path: '.build-cache/conclusion-state.json' });
  writeFileSync('.build-cache/conclusion-snapshot.json', JSON.stringify(state));
});
