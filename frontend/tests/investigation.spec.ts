import { test, expect, type Page } from '@playwright/test';
import { PerspectiveCamera, Vector3 } from 'three';
const sessionKey = 'detective-scenic:server-session:v6';
async function go(page: Page, name: string) {
  const link = page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true });
  if (await link.getAttribute('aria-current') !== 'page') await link.click();
  await expect(link).toHaveAttribute('aria-current', 'page');
}
async function snapshot(page: Page) {
  const saved = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!), sessionKey);
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  return response.json();
}
async function objectMenu(page: Page, name: string) {
  await go(page, '现场探索');
  const list = page.locator('details');
  if (await list.getAttribute('open') === null) await page.getByText('物品列表（备用入口）', { exact: true }).click();
  await list.getByRole('button', { name, exact: true }).click();
  return page.getByRole('dialog', { name: `${name}操作`, exact: true });
}
async function read(page: Page, name: string, content: string) {
  const menu = await objectMenu(page, name);
  await expect(menu).toContainText(content);
  await page.keyboard.press('Escape');
  await expect(menu).toHaveCount(0);
}
async function question(page: Page, target: string, action: string) {
  await go(page, '人物问询');
  const fallback = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  if (await fallback.getAttribute('open') === null) await fallback.locator('summary').click();
  await page.getByRole('button', { name: target, exact: true }).click();
  await page.getByRole('button', { name: action, exact: true }).click();
  await expect(page.getByRole('button', { name: action, exact: true })).toHaveCount(0);
}
async function gather(page: Page) {
  await read(page, '书桌', '上校被发现伏在书桌前');
  await read(page, '座钟', '18:22');
  await read(page, '纸张原位置', '18:20');
  await question(page, '克莱门特牧师', '询问：座钟平时准吗？');
  await question(page, '玛丽', '询问：上校什么时候到？');
  await question(page, '玛丽', '追问：18:15是看哪一座钟？');
}
async function fillComparison(page: Page, time: string, assessment: string) {
  await go(page, '线索整理');
  const panel = page.getByRole('region', { name: '我的时间核对', exact: true });
  for (const name of ['座钟读数', '牧师谈座钟', '玛丽的时间依据']) await panel.getByRole('checkbox', { name, exact: true }).check();
  await panel.getByLabel('换算后的时刻（24小时制）').fill(time);
  await panel.getByLabel('我的判断', { exact: true }).selectOption(assessment);
  await panel.getByRole('button', { name: '提交核对' }).click();
}
async function modelPosition(page: Page, position: [number, number, number]) {
  const canvas = page.locator('canvas');
  await canvas.scrollIntoViewIfNeeded();
  const frame = await page.getByLabel('书房三维场景', { exact: true }).boundingBox();
  await expect.poll(async () => (await canvas.boundingBox())?.height).toBe(frame!.height);
  const box = await canvas.boundingBox();
  expect(box).not.toBeNull();
  const camera = new PerspectiveCamera(43, box!.width / box!.height, 0.1, 1000);
  camera.position.set(4, 3.5, 5);
  camera.lookAt(0, 0.95, 0);
  camera.updateMatrixWorld();
  const p = new Vector3(...position).project(camera);
  return { x: box!.x + (p.x + 1) * box!.width / 2, y: box!.y + (1 - p.y) * box!.height / 2 };
}
async function clickModel(page: Page, position: [number, number, number], label: string) {
  await expect(page.locator('canvas')).toBeVisible();
  // Project into the real canvas and raycast the model, without HTML hotspot buttons.
  await expect(async () => {
    const p = await modelPosition(page, position);
    await page.mouse.click(p.x, p.y);
    await expect(page.getByRole('dialog', { name: `${label}操作`, exact: true })).toBeVisible({ timeout: 400 });
  }).toPass({ timeout: 5000 });
}

test('player investigates, calculates and makes a judgment without a prewritten answer', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '当前阶段 · 书房初查' })).toBeVisible();
  const initial = await snapshot(page);
  expect(JSON.stringify(initial)).not.toContain('18:07');
  expect(JSON.stringify(initial)).not.toContain('convertedTimeMinutes');
  expect(initial.clues).toHaveLength(0);
  await expect(page.getByText('座钟不能直接确定死亡时间', { exact: false })).toHaveCount(0);
  await gather(page);
  const gathered = await snapshot(page);
  expect(JSON.stringify(gathered)).not.toContain('18:07');
  expect(JSON.stringify(gathered)).not.toContain('不能保证');
  await fillComparison(page, '18:22', 'conflict');
  await expect(page.getByRole('region', { name: '我的时间核对', exact: true })).toContainText('这份核对还未完成');
  await expect(page.getByRole('heading', { name: '阶段完成 · 书房初查' })).toHaveCount(0);
  expect((await snapshot(page)).sequence.value).toBe(6);
  await fillComparison(page, '18:07', 'conflict');
  await expect(page.getByRole('heading', { name: '阶段完成 · 书房初查' })).toBeVisible();
  const finished = await snapshot(page);
  expect(finished.stage.completed).toBe(true);
  expect(finished.clues).toHaveLength(7);
  expect(finished.journal).toHaveLength(7);
  await page.reload();
  await expect(page.getByRole('heading', { name: '阶段完成 · 书房初查' })).toBeVisible();
  expect(await snapshot(page)).toEqual(finished);
  await page.context().storageState({ path: '.build-cache/browser-restart-state.json' });
  await page.screenshot({ path: 'test-results/study-interactions-complete.png', fullPage: true });
  expect(errors).toEqual([]);
});

test('clicking models opens live drawer and window menus, and paper opens its contents', async ({ page }) => {
  await page.goto('/');
  await clickModel(page, [0.8, 0.94, 0.48], '抽屉');
  const drawer = page.getByRole('dialog', { name: '抽屉操作', exact: true });
  await drawer.getByRole('button', { name: '打开抽屉', exact: true }).click();
  await drawer.getByRole('button', { name: '推回抽屉', exact: true }).click();
  await expect(drawer.getByRole('button', { name: '打开抽屉', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await clickModel(page, [1.4, 2, -2.02], '窗户');
  await page.getByRole('dialog', { name: '窗户操作', exact: true }).getByRole('button', { name: '打开窗户', exact: true }).click();
  await expect(page.getByRole('button', { name: '关上窗户', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await clickModel(page, [0, 1.27, 0.12], '纸张原位置');
  await expect(page.getByRole('dialog', { name: '纸张原位置操作', exact: true })).toContainText('亲爱的克莱门特');
  const saved = await snapshot(page);
  expect(saved.scene.windowOpened).toBe(true);
  expect(saved.scene.drawerOpened).toBe(false);
  expect(saved.clues).toHaveLength(1);
  await page.screenshot({ path: 'test-results/study-item-menu.png', fullPage: true });
  await page.reload();
  await expect(page.getByRole('heading', { name: '当前阶段 · 书房初查' })).toBeVisible();
  expect(await snapshot(page)).toEqual(saved);
  const window = await objectMenu(page, '窗户');
  await window.getByRole('button', { name: '关上窗户', exact: true }).click();
  await expect(window.getByRole('button', { name: '打开窗户', exact: true })).toBeVisible();
});

test('left drag pans the view without selecting a model; clicking an item preserves the camera', async ({ page }) => {
  await page.goto('/');
  await clickModel(page, [0.8, 0.94, 0.48], '抽屉');
  await page.keyboard.press('Escape');
  await page.mouse.move(1, 1);
  const before = await page.locator('canvas').screenshot();
  const point = await modelPosition(page, [0, 1.27, 0.12]);
  await page.mouse.move(point.x, point.y);
  await page.mouse.down();
  await page.mouse.move(point.x + 95, point.y + 35, { steps: 12 });
  await page.mouse.up();
  await page.mouse.move(1, 1);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const panned = await page.locator('canvas').screenshot();
  expect(panned.equals(before)).toBe(false);
  expect((await snapshot(page)).sequence.value).toBe(0);
  const lamp = await objectMenu(page, '台灯');
  await expect(lamp).toContainText('金属灯座');
  await page.keyboard.press('Escape');
  await page.mouse.move(1, 1);
  const afterSelection = await page.locator('canvas').screenshot();
  expect(afterSelection.equals(panned)).toBe(true);
  await go(page, '线索整理');
  await expect(page.locator('canvas')).toHaveCount(0);
  await go(page, '现场探索');
  await expect.poll(async () => (await page.locator('canvas').screenshot()).equals(panned)).toBe(true);
  await page.getByRole('button', { name: '恢复全景', exact: true }).click();
  await page.mouse.move(1, 1);
  await expect.poll(async () => (await page.locator('canvas').screenshot()).equals(before)).toBe(true);
});

test('lost automatic reading response retries the same request after reload', async ({ page }) => {
  await page.goto('/');
  const payloads: string[] = [];
  let drop = true;
  await page.route('**/api/investigate', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await objectMenu(page, '纸张原位置');
  await expect(page.getByRole('button', { name: '用原请求重试' })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: '用原请求重试' }).click();
  await expect(page.getByRole('button', { name: '用原请求重试' })).toHaveCount(0);
  const menu = await objectMenu(page, '纸张原位置');
  await expect(menu).toContainText('18:20');
  expect(payloads).toHaveLength(2);
  expect(payloads[0]).toBe(payloads[1]);
  const saved = await snapshot(page);
  expect(saved.sequence.value).toBe(1);
  expect(saved.journal).toHaveLength(1);
});

test('lost player-comparison response also retries the exact judgment and evidence', async ({ page }) => {
  await page.goto('/');
  await gather(page);
  const payloads: string[] = [];
  let drop = true;
  await page.route('**/api/comparetimes', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await fillComparison(page, '18:07', 'conflict');
  await expect(page.getByRole('button', { name: '用原请求重试' })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: '用原请求重试' }).click();
  await expect(page.getByRole('button', { name: '用原请求重试' })).toHaveCount(0);
  expect(payloads).toHaveLength(2);
  expect(payloads[0]).toBe(payloads[1]);
  const saved = await snapshot(page);
  expect(saved.sequence.value).toBe(7);
  expect(saved.journal).toHaveLength(7);
  expect(saved.stage.completed).toBe(true);
});

test('post-commit view failure only rereads the view', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '当前阶段 · 书房初查' })).toBeVisible();
  let writes = 0;
  await page.route('**/api/investigate', async route => { writes++; await route.continue(); });
  let fail = true;
  await page.route('**/api/getinvestigationsnapshot', async route => {
    if (fail) { fail = false; await route.abort('failed'); } else await route.continue();
  });
  await objectMenu(page, '座钟');
  await page.getByRole('button', { name: '重新读取视图' }).click();
  await go(page, '线索整理');
  await expect(page.getByRole('heading', { name: '座钟读数', exact: true })).toBeVisible();
  expect(writes).toBe(1);
});

test('mobile item menu fits after resizing and can be dismissed', async ({ page }) => {
  await page.goto('/');
  const menu = await objectMenu(page, '纸张原位置');
  await expect(menu).toContainText('18:20');
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  const menuBox = await menu.boundingBox();
  const frame = await page.getByLabel('书房三维场景', { exact: true }).boundingBox();
  expect(menuBox!.x).toBeGreaterThanOrEqual(frame!.x);
  expect(menuBox!.x + menuBox!.width).toBeLessThanOrEqual(frame!.x + frame!.width);
  expect(menuBox!.y + menuBox!.height).toBeLessThanOrEqual(frame!.y + frame!.height);
  await page.screenshot({ path: 'test-results/study-mobile-menu.png', fullPage: true });
  await menu.getByRole('button', { name: '关闭物品菜单' }).click();
  await expect(menu).toHaveCount(0);
});

test('witness-first investigation and optional evidence presentation remain available', async ({ page }) => {
  await page.goto('/');
  await question(page, '玛丽', '询问：上校什么时候到？');
  await question(page, '玛丽', '追问：18:15是看哪一座钟？');
  await question(page, '克莱门特牧师', '询问：座钟平时准吗？');
  await expect(page.getByRole('region', { name: '当面询问', exact: true }).getByRole('article', { name: '刚取得的信息' })).toContainText('拨快一刻钟');
  await expect(page.getByRole('button', { name: '出示座钟记录', exact: true })).toHaveCount(0);
  await read(page, '座钟', '18:22');
  await question(page, '克莱门特牧师', '出示座钟记录');
  const partial = await snapshot(page);
  expect(partial.stage.completed).toBe(false);
  await page.reload();
  const fallback = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  if (await fallback.getAttribute('open') === null) await fallback.locator('summary').click();
  await page.getByRole('button', { name: '克莱门特牧师', exact: true }).click();
  await expect(page.getByRole('heading', { name: '向牧师出示座钟记录', exact: true })).toBeVisible();
  expect(await snapshot(page)).toEqual(partial);
});

test('backend unavailable does not fabricate local progress', async ({ page }) => {
  await page.route('**/api/**', route => route.abort('failed'));
  await page.goto('/');
  await expect(page.getByRole('button', { name: '重新连接' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '当前阶段 · 书房初查' })).toHaveCount(0);
});


test('independent pages share one session and preserve a comparison draft during navigation', async ({ page }) => {
  await page.goto('/clues');
  await expect(page.getByRole('heading', { name: '线索整理', exact: true })).toBeVisible();
  await expect(page.locator('canvas')).toHaveCount(0);
  await expect(page.getByRole('region', { name: '当面询问', exact: true })).toHaveCount(0);
  const initial = await snapshot(page);
  await read(page, '座钟', '18:22');
  await expect(page.getByRole('region', { name: '我的时间核对', exact: true })).toHaveCount(0);
  await go(page, '线索整理');
  const form = page.getByRole('region', { name: '我的时间核对', exact: true });
  await form.getByRole('checkbox', { name: '座钟读数', exact: true }).check();
  await form.getByLabel('换算后的时刻（24小时制）').fill('18:11');
  await form.getByLabel('我的判断', { exact: true }).selectOption('uncertain');
  await question(page, '克莱门特牧师', '询问：座钟平时准吗？');
  await expect(page.locator('canvas')).toHaveCount(0);
  await expect(page.getByRole('region', { name: '我的时间核对', exact: true })).toHaveCount(0);
  await go(page, '线索整理');
  await expect(form.getByLabel('换算后的时刻（24小时制）')).toHaveValue('18:11');
  await expect(form.getByLabel('我的判断', { exact: true })).toHaveValue('uncertain');
  await expect(form.getByRole('checkbox', { name: '座钟读数', exact: true })).toBeChecked();
  const saved = await snapshot(page);
  expect(saved.sessionId).toBe(initial.sessionId);
  expect(saved.sequence.value).toBe(2);
  expect(saved.clues).toHaveLength(2);
  await page.goBack();
  await expect(page).toHaveURL(/\/interviews$/);
  const fallback1 = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  if (await fallback1.getAttribute('open') === null) await fallback1.locator('summary').click();
  await page.getByRole('button', { name: '克莱门特牧师', exact: true }).click();
  await expect(page.getByRole('region', { name: '已记录的答复', exact: true })).toContainText('拨快一刻钟');
  await page.goForward();
  await expect(page).toHaveURL(/\/clues$/);
  await expect(form.getByLabel('换算后的时刻（24小时制）')).toHaveValue('18:11');
  await page.reload();
  await expect(page.getByRole('heading', { name: '线索整理', exact: true })).toBeVisible();
  expect(await snapshot(page)).toEqual(saved);
  await page.screenshot({ path: 'test-results/pages-clues.png', fullPage: true });
  await go(page, '人物问询');
  const fallback2 = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  if (await fallback2.getAttribute('open') === null) await fallback2.locator('summary').click();
  await page.getByRole('button', { name: '克莱门特牧师', exact: true }).click();
  await page.screenshot({ path: 'test-results/pages-interviews.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('navigation during an in-flight reading keeps the pending request and its result', async ({ page }) => {
  await page.goto('/scene');
  await expect(page.getByRole('heading', { name: '现场探索', exact: true })).toBeVisible();
  let release: () => void = () => {};
  const gate = new Promise<void>(resolve => { release = resolve; });
  let requested: () => void = () => {};
  const started = new Promise<void>(resolve => { requested = resolve; });
  let writes = 0;
  await page.route('**/api/investigate', async route => {
    writes++;
    requested();
    const response = await route.fetch();
    await gate;
    await route.fulfill({ response });
  });
  await objectMenu(page, '纸张原位置');
  await started;
  await go(page, '线索整理');
  await expect(page.getByRole('button', { name: '提交核对', exact: true })).toBeDisabled();
  release();
  await expect(page.getByRole('heading', { name: '纸张摘录', exact: true })).toBeVisible();
  expect(writes).toBe(1);
  expect((await snapshot(page)).sequence.value).toBe(1);
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).pending, sessionKey)).toBeNull();
});

test('bookmarked page URLs and unknown paths render the appropriate page', async ({ page }) => {
  await page.goto('/interviews');
  await expect(page.getByRole('heading', { name: '人物问询', exact: true })).toBeVisible();
  await expect(page.locator('canvas')).toHaveCount(0);
  const saved = await snapshot(page);
  await page.goto('/scene');
  await expect(page.getByRole('heading', { name: '现场探索', exact: true })).toBeVisible();
  expect((await snapshot(page)).sessionId).toBe(saved.sessionId);
  await page.goto('/missing-page');
  await expect(page.getByRole('heading', { name: '找不到这个页面', exact: true })).toBeVisible();
  await expect(page.locator('canvas')).toHaveCount(0);
  await go(page, '线索整理');
  await expect(page.getByRole('heading', { name: '线索整理', exact: true })).toBeVisible();
});
