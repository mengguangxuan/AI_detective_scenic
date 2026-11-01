import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
const headers = { 'X-Detective-Client': 'web' };
const key = 'detective-scenic:server-session:v6';
async function start(page: Page) {
  const identity = await page.request.post('/api/openlocalidentity', { headers, data: {} }); expect(identity.status()).toBe(200);
  const sessionId = randomUUID();
  const opened = await page.request.post('/api/openinvestigation', { headers, data: { sessionId, caseId: 'vicarage_study', revision: { value: 24 } } });
  expect(opened.status()).toBe(200);
  await page.addInitScript(({ key, sessionId }) => { if (!localStorage.getItem(key)) localStorage.setItem(key, JSON.stringify({ version: 1, sessionId, creationPending: false, pending: null })); }, { key, sessionId });
  async function snapshot() { const response = await page.request.post('/api/getinvestigationsnapshot', { headers, data: { sessionId } }); expect(response.status()).toBe(200); return response.json(); }
  return { snapshot, sessionId };
}
async function travel(page: Page, label: string) {
  await page.getByRole('region', { name: '可行走的路线', exact: true }).getByRole('button', { name: label, exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '调查结果已保存，当前视图已更新。' })).toBeVisible();
}
async function item(page: Page, label: string) {
  const list = page.getByRole('region', { name: '现场探索', exact: true }).locator('details');
  if (!(await list.evaluate(node => (node as HTMLDetailsElement).open))) await list.locator('summary').click();
  await list.getByRole('button', { name: label, exact: true }).click();
}
test('woodland exploration renders a second scene and requires close inspection before collecting a sample', async ({ page }) => {
  const game = await start(page); await page.goto('/map');
  const first = await game.snapshot();
  const denied = await page.request.post('/api/executeoperation', { headers, data: { sessionId: game.sessionId, requestId: randomUUID(), expectedSequence: first.sequence, operation: 'brush_woodland_leaves' } });
  expect(denied.ok()).toBe(false);
  await travel(page, '前往树林');
  const frame = page.getByLabel('树林三维场景', { exact: true });
  await expect(frame.locator('canvas[data-scene-ready="woodland"]')).toBeVisible();
  await expect(page.getByText('正在准备林地…', { exact: true })).toHaveCount(0);
  expect((await game.snapshot()).scene.playerPosition).toEqual({ x: 0, y: 0, z: 5 });
  expect((await game.snapshot()).clues.some((c: { id: string }) => c.id === 'clue_woodland_sample')).toBe(false);
  mkdirSync('../docs/frontend/images', { recursive: true });
  await frame.screenshot({ path: '../docs/frontend/images/woodland-entrance.png' });
  await travel(page, '沿小径走向空地'); await travel(page, '走近倒木旁');
  expect((await game.snapshot()).scene.playerPosition).toEqual({ x: 2.8, y: 0, z: -4.5 });
  // Wait for the authored walking animation before checking the actual mesh hit target.
  await page.waitForTimeout(1600);
  await frame.screenshot({ path: '../docs/frontend/images/woodland-clearing.png' });
  const box = await frame.boundingBox(); expect(box).not.toBeNull();
  await frame.click({ position: { x: box!.width * 0.5, y: box!.height * 0.55 } });
  const menu = page.getByRole('dialog', { name: '倒木旁的落叶操作', exact: true });
  await expect(menu).toBeVisible(); await menu.getByRole('button', { name: '拨开落叶查看', exact: true }).click();
  await expect.poll(async () => (await game.snapshot()).scene.objects.some((o: { node: string }) => o.node === 'node_woodland_fragment')).toBe(true);
  expect((await game.snapshot()).clues.some((c: { id: string }) => c.id === 'clue_woodland_sample')).toBe(false);
  await item(page, '棕色小碎块');
  await expect.poll(async () => (await game.snapshot()).clues.filter((c: { id: string }) => c.id === 'clue_woodland_sample').length).toBe(1);
  await travel(page, '返回牧师寓所'); await expect(page.getByLabel('书房三维场景', { exact: true })).toBeVisible();
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '警方交流', exact: true }).click();
  await page.getByRole('button', { name: '交送树林样本，请海多克辨认', exact: true }).click();
  await expect.poll(async () => (await game.snapshot()).externalChecks.some((c: { id: string; progress: unknown }) => c.id === 'crystal_opinion' && c.progress)).toBe(true);
  await page.getByRole('button', { name: '整理并等待15分钟', exact: true }).click();
  await expect.poll(async () => (await game.snapshot()).clues.some((c: { id: string }) => c.id === 'clue_crystal_opinion')).toBe(true);
  await page.goto('/scene'); await travel(page, '前往树林'); await travel(page, '沿小径走向空地'); await travel(page, '走近倒木旁');
  await page.reload(); await expect(frame).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(frame.locator('canvas[data-scene-ready="woodland"]')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await frame.screenshot({ path: '../docs/frontend/images/woodland-mobile.png' });
  const saved = await game.snapshot(); expect(saved.clues.filter((c: { id: string }) => c.id === 'clue_woodland_sample')).toHaveLength(1);
  await page.context().storageState({ path: '.build-cache/woodland-exploration-browser-state.json' });
  writeFileSync('.build-cache/woodland-exploration-snapshot.json', JSON.stringify(saved));
});

test('a lost woodland entry response reuses the exact original request and does not create another move', async ({ page }) => {
  const game = await start(page); await page.goto('/map'); let lost = false;
  const sent: object[] = [];
  await page.route('**/api/executeoperation', async route => {
    if (route.request().postDataJSON().operation !== 'enter_woodland') return route.continue();
    sent.push(route.request().postDataJSON());
    if (!lost) { lost = true; await route.fetch(); return route.abort('failed'); }
    await route.continue();
  });
  await page.getByRole('button', { name: '前往树林', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  const committed = await game.snapshot(); expect(committed.scene.bundleId).toBe('woodland');
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByLabel('树林三维场景', { exact: true })).toBeVisible();
  expect(sent).toHaveLength(2); expect(sent[1]).toEqual(sent[0]);
  expect((await game.snapshot()).sequence).toEqual(committed.sequence);
});

test('woodland remains playable through the same registered actions without WebGL', async ({ page }) => {
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', { value: function (this: HTMLCanvasElement, kind: string, ...args: unknown[]) {
      return /webgl/i.test(kind) ? null : Reflect.apply(original, this, [kind, ...args]);
    } });
  });
  const game = await start(page); await page.goto('/map'); await travel(page, '前往树林');
  await expect(page.getByText(/浏览器不支持 WebGL|3D 场景无法加载/).first()).toBeVisible();
  await travel(page, '沿小径走向空地'); await travel(page, '走近倒木旁');
  await item(page, '倒木旁的落叶'); await page.getByRole('button', { name: '拨开落叶查看', exact: true }).click();
  await expect.poll(async () => (await game.snapshot()).scene.objects.some((o: { node: string }) => o.node === 'node_woodland_fragment')).toBe(true);
  await item(page, '棕色小碎块');
  await expect.poll(async () => (await game.snapshot()).clues.some((c: { id: string }) => c.id === 'clue_woodland_sample')).toBe(true);
  await travel(page, '返回牧师寓所'); expect((await game.snapshot()).scene.bundleId).toBe('study');
});
