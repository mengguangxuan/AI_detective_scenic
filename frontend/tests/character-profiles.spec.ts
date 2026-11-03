import { test, expect } from './support/investigation-test';

test('unified character authoring keeps public profiles and the current browser contract', async ({ page }) => {
  await page.goto('/case');
  await expect(page.getByRole('region', { name: '案中人物', exact: true })).toBeVisible();
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:investigation-session')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  const state = await response.json();
  expect(state.people).toHaveLength(14);
  expect(state.witnesses.some((p: { id: string }) => p.id === 'melchett')).toBe(true);
  expect(JSON.stringify(state)).not.toMatch(/privateContext|"policy"|"dossier"/);
});
