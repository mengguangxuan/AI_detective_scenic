import { writeFileSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
import { PerspectiveCamera, Vector3 } from 'three';
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200); return response.json();
}
async function item(page: Page, name: string) {
  const list = page.locator('details').filter({ has: page.getByText('物品列表（备用入口）', { exact: true }) });
  if (!(await list.evaluate(n => (n as HTMLDetailsElement).open))) await list.locator('summary').click();
  await list.getByRole('button', { name, exact: true }).click();
  return page.getByRole('dialog', { name: `${name}操作`, exact: true });
}
async function model(page: Page, position: [number, number, number], label: string) {
  const canvas = page.locator('canvas');
  await expect(canvas).toBeVisible();
  const frame = await page.getByLabel('书房三维场景', { exact: true }).boundingBox();
  await expect.poll(async () => (await canvas.boundingBox())?.height).toBe(frame!.height);
  await expect(async () => {
    await canvas.scrollIntoViewIfNeeded();
    const box = (await canvas.boundingBox())!;
    const camera = new PerspectiveCamera(43, box.width / box.height, 0.1, 1000);
    camera.position.set(4, 3.5, 5); camera.lookAt(0, 0.95, 0); camera.updateMatrixWorld();
    const p = new Vector3(...position).project(camera);
    await page.mouse.click(box.x + (p.x + 1) * box.width / 2, box.y + (1 - p.y) * box.height / 2);
    await expect(page.getByRole('dialog', { name: `${label}操作`, exact: true })).toBeVisible({ timeout: 400 });
  }).toPass({ timeout: 7000 });
}

test('player lifts the real planter model, inspects the exposed cavity and retains the discovery after replacing it', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  await page.goto('/scene');
  await expect(page.getByRole('heading', { name: '当前阶段 · 书房初查', exact: true })).toBeVisible();
  const first = await snapshot(page);
  expect(first.caseRevision.value).toBe(20); expect(first.scene.revision).toBe('5');
  expect(first.scene.objects.some((o: { node: string }) => o.node === 'node_planter_interior')).toBe(false);
  await model(page, [2.35, 1.15, -1.08], '窗边盆栽');
  await page.getByRole('button', { name: '提起内盆查看', exact: true }).click();
  await expect(page.getByRole('button', { name: '放回内盆', exact: true })).toBeVisible();
  expect((await snapshot(page)).clues).toHaveLength(0);
  await page.keyboard.press('Escape');
  await page.reload();
  await expect(page.locator('canvas')).toBeVisible();
  await model(page, [2.35, 1.18, -1.65], '外盆内部');
  await expect(page.getByRole('dialog', { name: '外盆内部操作', exact: true })).toContainText('盆底和侧边留有空隙');
  const found = await snapshot(page);
  expect(found.clues.map((c: { id: string }) => c.id)).toEqual(['clue_planter']);
  await page.keyboard.press('Escape');
  const menu = await item(page, '窗边盆栽');
  await menu.getByRole('button', { name: '放回内盆', exact: true }).click();
  const closed = await snapshot(page);
  expect(closed.scene.objects.some((o: { node: string }) => o.node === 'node_planter_interior')).toBe(false);
  expect(closed.clues).toEqual(found.clues);
  await page.keyboard.press('Escape'); await page.reload(); expect(await snapshot(page)).toEqual(closed);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect((await item(page, '窗边盆栽')).getByRole('button', { name: '提起内盆查看', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: '.build-cache/planter-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
  await page.context().storageState({ path: '.build-cache/planter-state.json' });
  writeFileSync('.build-cache/planter-snapshot.json', JSON.stringify(closed));
});

test('fallback inspection retries a lost successful response without granting a second clue', async ({ page }) => {
  await page.goto('/scene');
  const menu = await item(page, '窗边盆栽');
  await menu.getByRole('button', { name: '提起内盆查看', exact: true }).click();
  await expect(menu.getByRole('button', { name: '放回内盆', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  let attempts = 0;
  await page.route('**/api/investigate', async route => {
    attempts++;
    const response = await route.fetch();
    if (attempts === 1) { await route.abort('failed'); return; }
    await route.fulfill({ response });
  });
  await item(page, '外盆内部');
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  const state = await snapshot(page);
  expect(attempts).toBe(2); expect(state.sequence.value).toBe(2);
  expect(state.clues.map((c: { id: string }) => c.id)).toEqual(['clue_planter']);
  await page.reload(); expect(await snapshot(page)).toEqual(state);
});

test.describe('actual previous revision compatibility', () => {
  test.use({ storageState: '.build-cache/pre-planter-v17-state.json' });
  test('revision 17 keeps its assessment history and original study without new planter bindings', async ({ page }) => {
    await page.goto('/scene');
    await expect(page.getByRole('status').filter({ hasText: '已恢复原修订的调查' })).toBeVisible();
    const state = await snapshot(page);
    expect(state.caseRevision.value).toBe(17); expect(state.scene.revision).toBe('4');
    expect(state.reasoning.attempts.length).toBeGreaterThan(0);
    expect(state.scene.objects.some((o: { node: string }) => o.node === 'node_planter')).toBe(false);
    await page.reload(); expect(await snapshot(page)).toEqual(state);
  });
});
