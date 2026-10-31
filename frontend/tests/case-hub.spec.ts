import { test, expect, type Page } from '@playwright/test';
const key = 'detective-scenic:server-session:v6';
const headers = { 'X-Detective-Client': 'web' };
async function savedList(page: Page) {
  const response = await page.request.post('/api/listinvestigations', { data: {}, headers });
  expect(response.status()).toBe(200); return response.json();
}
async function state(page: Page) {
  const saved = await page.evaluate(locatorKey => JSON.parse(localStorage.getItem(locatorKey)!), key);
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers });
  expect(response.status()).toBe(200); return response.json();
}
async function begin(page: Page) {
  await page.goto('/cases/vicarage_study');
  await page.getByRole('button', { name: '开始新调查', exact: true }).click();
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
}

test('first visit shows the theatre and catalogue without starting or restoring a game', async ({ page }) => {
  const gameRequests: string[] = [];
  page.on('request', request => { if (/\/api\/(openstudy|openinvestigation|getinvestigationsnapshot)$/.test(request.url())) gameRequests.push(request.url()); });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '真相，等你亲自发现。', exact: true })).toBeVisible();
  await expect(page.getByText('还没有保存的调查。先选择一个案件开始吧。', { exact: true })).toBeVisible();
  const artwork = page.getByRole('img', { name: '原创侦探与年轻助手的雾夜插画，配文：真相只有一个', exact: true });
  await expect(artwork).toBeVisible();
  await expect.poll(() => artwork.evaluate(node => (node as HTMLImageElement).naturalWidth)).toBe(1672);
  await artwork.evaluate(node => (node as HTMLImageElement).decode());
  expect((await artwork.boundingBox())!.height).toBeGreaterThan(300);
  await page.screenshot({ path: '.build-cache/home-hero-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: '.build-cache/home-hero-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1280, height: 720 });
  expect(await savedList(page)).toEqual([]);
  await page.getByRole('link', { name: '进入案件区', exact: true }).click();
  await expect(page).toHaveURL(/\/cases$/);
  await page.getByRole('link', { name: '查看案件', exact: true }).click();
  await expect(page.getByRole('region', { name: '案件介绍', exact: true })).toBeVisible();
  expect(gameRequests).toEqual([]);
  expect(await page.evaluate(locatorKey => localStorage.getItem(locatorKey), key)).toBeNull();
  await expect(page.locator('body')).not.toContainText('安妮实施射击');
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: '.build-cache/case-hub-mobile.png', fullPage: true });
});

test('explicit start, a second investigation and restoring the first keep independent progress', async ({ page }) => {
  await begin(page);
  const first = await state(page);
  await page.getByRole('link', { name: '现场探索', exact: true }).click();
  await page.getByText('物品列表（备用入口）', { exact: true }).click();
  await page.getByRole('button', { name: '座钟', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '座钟操作', exact: true })).toContainText('18:22');
  await page.keyboard.press('Escape');
  const learned = await state(page);
  await page.getByRole('button', { name: '返回案件区', exact: true }).click();
  await expect(page.getByRole('heading', { name: '我的调查', exact: true })).toBeVisible();
  await page.getByRole('link', { name: '查看案件', exact: true }).click();
  await page.getByRole('button', { name: '开始新调查', exact: true }).click();
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  const second = await state(page);
  expect(second.sessionId).not.toBe(first.sessionId); expect(second.clues).toEqual([]);
  await page.getByRole('button', { name: '返回案件区', exact: true }).click();
  await expect(page.getByRole('button', { name: '继续调查', exact: true })).toHaveCount(2);
  const original = page.getByRole('listitem').filter({ hasText: '1条记录' });
  await original.getByRole('button', { name: '继续调查', exact: true }).click();
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  expect(await state(page)).toEqual(learned);
  await page.reload(); expect(await state(page)).toEqual(learned);
  const listing = await savedList(page); expect(listing).toHaveLength(2);
  await page.context().storageState({ path: '.build-cache/case-hub-state.json' });
});

test('a lost opening response preserves the exact selected case and resumes one investigation', async ({ page }) => {
  const payloads: string[] = []; let drop = true;
  await page.route('**/api/openinvestigation', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.goto('/cases/vicarage_study');
  await page.getByRole('button', { name: '开始新调查', exact: true }).click();
  await expect(page.getByRole('button', { name: '重新连接', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '返回案件区', exact: true }).click();
  await expect(page.getByRole('button', { name: '返回待确认调查', exact: true })).toBeVisible();
  await page.getByRole('link', { name: '查看案件', exact: true }).click();
  await expect(page.getByRole('button', { name: '开始新调查', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '返回待确认调查', exact: true }).click();
  await page.getByRole('button', { name: '重新连接', exact: true }).click();
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  expect(payloads).toHaveLength(2); expect(payloads[0]).toBe(payloads[1]);
  expect(await savedList(page)).toHaveLength(1);
});

test('leaving to the hub keeps the current reasoning draft, and switching never imports it into another investigation', async ({ page }) => {
  await begin(page);
  await page.getByRole('link', { name: '推理与复盘', exact: true }).click();
  const text = page.getByLabel('你的推理（人物责任、动机、经过与证据之间的联系）', { exact: true });
  await text.fill('未提交的测试草稿，只属于第一份调查。');
  await page.getByRole('button', { name: '返回案件区', exact: true }).click();
  await page.getByRole('button', { name: '继续调查', exact: true }).click();
  await page.getByRole('link', { name: '推理与复盘', exact: true }).click();
  await expect(text).toHaveValue('未提交的测试草稿，只属于第一份调查。');
  await page.getByRole('button', { name: '新建调查', exact: true }).click();
  await page.getByRole('button', { name: '开始新调查', exact: true }).click();
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  await page.getByRole('link', { name: '推理与复盘', exact: true }).click();
  await expect(text).toHaveValue('');
});

test('an unknown mutation result blocks changing investigations and retains the original retry payload', async ({ page }) => {
  await begin(page);
  let writes = 0;
  await page.route('**/api/investigate', async route => { writes++; if (writes === 1) { await route.fetch(); await route.abort('failed'); } else await route.continue(); });
  await page.getByRole('link', { name: '现场探索', exact: true }).click();
  await page.getByText('物品列表（备用入口）', { exact: true }).click();
  await page.getByRole('button', { name: '座钟', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  const pending = await page.evaluate(locatorKey => JSON.parse(localStorage.getItem(locatorKey)!).pending, key);
  await page.goto('/cases/vicarage_study');
  await expect(page.getByRole('button', { name: '开始新调查', exact: true })).toBeDisabled();
  expect(await page.evaluate(locatorKey => JSON.parse(localStorage.getItem(locatorKey)!).pending, key)).toEqual(pending);
  await page.getByRole('button', { name: '返回待确认调查', exact: true }).click();
  await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(writes).toBe(2); expect((await state(page)).sequence.value).toBe(1);
});

test('an investigation bookmark without a locator asks for a case and does not silently start', async ({ page }) => {
  await page.goto('/scene');
  await expect(page.getByText('请先选择案件，或从“我的调查”恢复一份进度。', { exact: true })).toBeVisible();
  await expect(page.getByText('还没有保存的调查。先选择一个案件开始吧。', { exact: true })).toBeVisible();
  expect(await savedList(page)).toEqual([]);
});

test('an unreadable locator is preserved and does not get replaced by a new investigation', async ({ page }) => {
  await page.addInitScript(locatorKey => localStorage.setItem(locatorKey, 'invalid-json'), key);
  await page.goto('/cases/vicarage_study');
  await expect(page.getByRole('alert')).toContainText('无法读取本机调查定位');
  await expect(page.getByRole('button', { name: '开始新调查', exact: true })).toBeDisabled();
  expect(await page.evaluate(locatorKey => localStorage.getItem(locatorKey), key)).toBe('invalid-json');
  expect(await savedList(page)).toEqual([]);
});

test('an unavailable backend keeps the home page and allows retry without inventing catalogue data', async ({ page }) => {
  await page.route('**/api/**', route => route.abort('failed'));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '真相，等你亲自发现。', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '重新读取案件区', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '查看案件', exact: true })).toHaveCount(0);
});
