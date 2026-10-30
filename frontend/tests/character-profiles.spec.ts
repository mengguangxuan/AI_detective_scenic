import { test, expect } from '@playwright/test';
import { existsSync } from 'node:fs';

test('unified character authoring keeps public profiles and the current browser contract', async ({ page }) => {
  await page.goto('/case');
  await expect(page.getByRole('region', { name: '案中人物', exact: true })).toBeVisible();
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  const state = await response.json();
  expect(state.caseRevision.value).toBe(10);
  expect(state.people).toHaveLength(13);
  expect(state.witnesses.some((p: { id: string }) => p.id === 'melchett')).toBe(true);
  expect(JSON.stringify(state)).not.toMatch(/privateContext|"policy"|"dossier"/);
});

test.describe('real pre-refactor revision 9 save', () => {
  test.use({ storageState: '.build-cache/pre-character-v9-state.json' });
  test.skip(!existsSync('.build-cache/pre-character-v9-state.json'), 'Requires a saved revision 9 investigation.');
  test('original statements and supplementary phase still restore after the configuration refactor', async ({ page }) => {
    await page.goto('/clues');
    await expect(page.getByRole('region', { name: '供述版本对照', exact: true })).toContainText('第 1 条记录');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state.caseRevision.value).toBe(9);
    expect(state.followUps.every((p: { started: boolean }) => p.started)).toBe(true);
    expect(state.statementComparisons.every((p: { rows: { initial: unknown; revised: unknown }[] }) => p.rows.every(r => r.initial && r.revised))).toBe(true);
    await page.reload();
    await expect(page.getByRole('region', { name: '供述版本对照', exact: true })).toContainText('第 1 条记录');
  });
});

test.describe('real pre-merge revision 10 save', () => {
  test.use({ storageState: '.build-cache/pre-police-v10-state.json' });
  test.skip(!existsSync('.build-cache/pre-police-v10-state.json'), 'Requires a saved pre-merge investigation.');
  test('embedded police data restores once through the unified profile list', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('heading', { name: '警方交流', exact: true })).toBeVisible();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state.caseRevision.value).toBe(10);
    const officers = state.witnesses.filter((w: { point: string }) => w.point === state.police.point);
    expect(officers).toHaveLength(1);
    expect(state.police.sharedClues).toContain('clue_redding_revised');
    expect(state.followUps.every((p: { started: boolean }) => p.started)).toBe(true);
    expect(JSON.stringify(state)).not.toMatch(/privateContext|"dossier"/);
    await page.reload();
    await expect(page.getByRole('heading', { name: '警方交流', exact: true })).toBeVisible();
  });
});
