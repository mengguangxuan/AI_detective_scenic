import { readFileSync } from 'node:fs';
import { test, expect } from '@playwright/test';
// Prepare with tools/verify_case_storage_live.mjs, restart backend, then browser-restore.
test.use({ storageState: '.build-cache/storage-current/browser.json' });
test('a current investigation restores its exact snapshot and original investigation/wait receipts after backend restart', async ({ page }) => {
  const saved = JSON.parse(readFileSync('.build-cache/storage-current/investigation.json', 'utf8'));
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/scene');
  await expect(page.getByRole('navigation', { name: '调查导航' })).toBeVisible();
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '线索整理', exact: true }).click();
  await expect(page.getByText(saved.snapshot.clues[0].title, { exact: true }).first()).toBeVisible();
  const headers = { 'X-Detective-Client': 'web' };
  const snapshot = await page.request.post('/api/getinvestigationsnapshot', { headers, data: { sessionId: saved.sessionId } });
  expect(snapshot.status()).toBe(200); expect(await snapshot.json()).toEqual(saved.snapshot);
  const retry = await page.request.post('/api/investigate', { headers, data: saved.request });
  expect(retry.status()).toBe(200); expect(await retry.json()).toEqual(saved.receipt);
  const waitRetry = await page.request.post('/api/advancetime', { headers, data: saved.waitRequest });
  expect(waitRetry.status()).toBe(200); expect(await waitRetry.json()).toEqual(saved.waitReceipt);
  await page.reload();
  await expect(page.getByRole('navigation', { name: '调查导航' })).toBeVisible();
  expect(errors).toEqual([]);
});


test.describe('Speckled Band daytime after actual process restart', () => {
  test.use({ storageState: '.build-cache/speckled-daytime-state.json' });
  test('independent daytime rooms, notes and the original inspection receipt restore without replaying a night event', async ({ page }) => {
    const expected = JSON.parse(readFileSync('.build-cache/speckled-daytime-snapshot.json', 'utf8'));
    const original = JSON.parse(readFileSync('.build-cache/speckled-daytime-receipt.json', 'utf8'));
    await page.goto('/clues');
    await expect(page.getByRole('heading', { name: '线索整理', exact: true })).toBeVisible();
    const headers = { 'X-Detective-Client': 'web' };
    const response = await page.request.post('/api/getinvestigationsnapshot', { headers, data: { sessionId: expected.sessionId } });
    expect(response.status()).toBe(200); expect(await response.json()).toEqual(expected);
    const repeated = await page.request.post('/api/investigate', { headers, data: original.request });
    expect(repeated.status()).toBe(200); expect(await repeated.json()).toEqual(original.receipt);
    await expect(page.getByRole('heading', { name: '白天调查笔记', exact: true })).toBeVisible();
    expect(expected.stage.completed).toBe(true); expect(expected.reasoning).toBeNull();
  });
});
