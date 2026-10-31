import { test, expect } from '@playwright/test';
import { existsSync } from 'node:fs';

test('unified character authoring keeps public profiles and the current browser contract', async ({ page }) => {
  await page.goto('/case');
  await expect(page.getByRole('region', { name: '案中人物', exact: true })).toBeVisible();
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  const state = await response.json();
  expect(state.caseRevision.value).toBe(14);
  expect(state.people).toHaveLength(14);
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
    expect(state.people.some((p: { id: string }) => p.id === 'mrs_archer')).toBe(false);
    await expect(page.getByRole('status').filter({ hasText: '若要体验牧师来电来源核查' })).toBeVisible();
    const officers = state.witnesses.filter((w: { point: string }) => w.point === state.police.point);
    expect(officers).toHaveLength(1);
    expect(state.police.sharedClues).toContain('clue_redding_revised');
    expect(state.followUps.every((p: { started: boolean }) => p.started)).toBe(true);
    expect(JSON.stringify(state)).not.toMatch(/privateContext|"dossier"/);
    await page.reload();
    await expect(page.getByRole('heading', { name: '警方交流', exact: true })).toBeVisible();
  });
});


test.describe('real revision 11 investigation', () => {
  test.use({ storageState: '.build-cache/pre-paper-v11-state.json' });
  test.skip(!existsSync('.build-cache/pre-paper-v11-state.json'), 'Requires a saved revision 11 investigation.');
  test('original Archer progress restores without new paper checks or a revision migration', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('region', { name: '已联系的核查对象', exact: true })).toContainText('阿彻太太');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state.caseRevision.value).toBe(11);
    expect(state.externalChecks).toHaveLength(2);
    expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_archer_review')).toBe(true);
    await expect(page.getByRole('button', { name: '申请检查纸张原件', exact: true })).toHaveCount(0);
  });
});


test.describe('real revision 12 investigation', () => {
  test.use({ storageState: '.build-cache/pre-sightline-v12-state.json' });
  test.skip(!existsSync('.build-cache/pre-sightline-v12-state.json'), 'Requires a saved revision 12 investigation.');
  test('paper checks and original police records remain frozen when sightline checks are published', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('article', { name: '纸张笔迹比对', exact: true })).toContainText('已完成');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state.caseRevision.value).toBe(12);
    expect(state.externalChecks).toHaveLength(4);
    expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_paper_review')).toBe(true);
    await expect(page.getByRole('button', { name: '申请窗边视线复验', exact: true })).toHaveCount(0);
  });
});


test.describe('real revision 13 investigation', () => {
  test.use({ storageState: '.build-cache/pre-call-v13-state.json' });
  test.skip(!existsSync('.build-cache/pre-call-v13-state.json'), 'Requires a saved revision 13 investigation.');
  test('sightline findings remain frozen without new call checks or automatic migration', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('article', { name: '窗边视线复验', exact: true })).toContainText('已完成');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state.caseRevision.value).toBe(13);
    expect(state.externalChecks).toHaveLength(5);
    expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_sightline_review')).toBe(true);
    await expect(page.getByRole('button', { name: '申请追查牧师接到的电话', exact: true })).toHaveCount(0);
  });
});
