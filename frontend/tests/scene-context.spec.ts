import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const headers = { 'X-Detective-Client': 'web' };
async function start(page: Page) {
  const identity = await page.request.post('/api/openlocalidentity', { headers, data: {} }); expect(identity.status()).toBe(200);
  const sessionId = randomUUID();
  const opened = await page.request.post('/api/openinvestigation', { headers, data: { sessionId, caseId: 'vicarage_study' } });
  expect(opened.status()).toBe(200);
  await page.addInitScript(sessionId => { localStorage.setItem('detective-scenic:investigation-session', JSON.stringify({ version: 1, sessionId, creationPending: false, pending: null })); }, sessionId);
  await page.goto('/scene');
  return async () => {
    const result = await page.request.post('/api/getinvestigationsnapshot', { headers, data: { sessionId } }); expect(result.status()).toBe(200); return result.json();
  };
}
test('late context loss from disposed canvases cannot disable the current scene', async ({ page }) => {
  await start(page);
  const study = page.getByLabel('书房三维场景', { exact: true }).locator('canvas'); await expect(study).toBeVisible();
  await page.getByText('正在准备书房…', { exact: true }).waitFor({ state: 'hidden' });
  const oldStudy = await study.elementHandle(); expect(oldStudy).not.toBeNull();
  await page.getByRole('button', { name: '前往树林', exact: true }).click();
  const forest = page.getByLabel('树林三维场景', { exact: true }).locator('canvas[data-scene-ready="woodland"]'); await expect(forest).toBeVisible();
  expect(await oldStudy!.evaluate(canvas => canvas.isConnected)).toBe(false);
  await oldStudy!.evaluate(canvas => canvas.dispatchEvent(new Event('webglcontextlost', { cancelable: true })));
  await expect(forest).toBeVisible();
  const oldForest = await forest.elementHandle(); expect(oldForest).not.toBeNull();
  await page.getByRole('button', { name: '返回牧师寓所', exact: true }).click(); await expect(study).toBeVisible();
  await oldForest!.evaluate(canvas => canvas.dispatchEvent(new Event('webglcontextlost', { cancelable: true })));
  await expect(study).toBeVisible();
  await page.getByRole('button', { name: '前往树林', exact: true }).click(); await expect(forest).toBeVisible();
  await oldStudy!.dispose(); await oldForest!.dispose();
});

test('an active context can restore or retry its picture without refreshing or changing the investigation', async ({ page }) => {
  const snapshot = await start(page);
  await page.getByRole('button', { name: '前往树林', exact: true }).click();
  const canvas = page.getByLabel('树林三维场景', { exact: true }).locator('canvas[data-scene-ready="woodland"]'); await expect(canvas).toBeVisible();
  const before = await snapshot();
  const extension = await canvas.evaluateHandle(node => node.getContext('webgl2')!.getExtension('WEBGL_lose_context'));
  expect(await extension.evaluate(value => value !== null)).toBe(true);
  await extension.evaluate(value => value!.loseContext());
  await expect(page.getByText('三维画面暂时中断，正在等待恢复。也可重试画面或使用下方物品列表。', { exact: true })).toBeVisible();
  await expect(canvas).toHaveCount(1);
  await extension.evaluate(value => value!.restoreContext());
  await expect(page.getByRole('button', { name: '重试三维画面', exact: true })).toHaveCount(0);
  await expect.poll(() => canvas.evaluate(node => node.getContext('webgl2')!.isContextLost())).toBe(false);
  await extension.evaluate(value => value!.loseContext());
  await page.getByRole('button', { name: '重试三维画面', exact: true }).click();
  await expect(canvas).toBeVisible();
  await expect.poll(() => canvas.evaluate(node => node.getContext('webgl2')!.isContextLost())).toBe(false);
  expect(await snapshot()).toEqual(before);
  await extension.dispose();
});
