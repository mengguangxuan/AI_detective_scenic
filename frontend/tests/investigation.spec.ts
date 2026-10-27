import { test, expect } from '@playwright/test';
const secret = '信上写着：今晚九点见。';
const sessionKey = 'detective-scenic:server-session:v1';

test('real investigation, hidden content, browser restore and mobile layout', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByRole('button', { name: '书桌抽屉', exact: true })).toBeVisible();
  const saved = await page.evaluate(key => localStorage.getItem(key), sessionKey);
  expect(saved).not.toBeNull();
  const initial = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: JSON.parse(saved!).sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(initial.status()).toBe(200);
  expect(await initial.text()).not.toContain(secret);
  await expect(page.getByRole('button', { name: '抽屉中的信', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '书桌抽屉', exact: true }).click();
  await page.getByRole('button', { name: '打开抽屉', exact: true }).click();
  await page.getByRole('button', { name: '抽屉中的信', exact: true }).click();
  await page.getByRole('button', { name: '阅读信件', exact: true }).click();
  await expect(page.getByRole('heading', { name: '信件正文' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: '信件正文' })).toBeVisible();
  await expect(page.getByRole('button', { name: '在场景中选择信件' })).toBeVisible();
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).pending, sessionKey)).toBeNull();
  await page.context().storageState({ path: '.build-cache/browser-restart-state.json' });
  await page.screenshot({ path: 'test-results/investigation-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(errors).toEqual([]);
});

test('lost mutation response retries the identical request and creates one record', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '书桌抽屉', exact: true }).click();
  const payloads: string[] = [];
  let drop = true;
  await page.route('**/api/executeoperation', async route => {
    payloads.push(route.request().postData()!);
    if (drop) {
      drop = false;
      await route.fetch(); // The server commits; only its response is lost.
      await route.abort('failed');
    } else await route.continue();
  });
  await page.getByRole('button', { name: '打开抽屉', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('button', { name: '用原请求重试' })).toBeVisible();
  await page.getByRole('button', { name: '用原请求重试' }).click();
  await expect(page.getByRole('button', { name: '抽屉中的信', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '用原请求重试' })).toHaveCount(0);
  expect(payloads).toHaveLength(2);
  expect(payloads[0]).toBe(payloads[1]);
  const stored = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!), sessionKey);
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: stored.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  const snapshot = await response.json();
  expect(snapshot.sequence.value).toBe(1);
  expect(snapshot.journal).toHaveLength(1);
});

test('failed post-commit refresh only retries the snapshot', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '书桌抽屉', exact: true }).click();
  let writes = 0;
  await page.route('**/api/executeoperation', async route => { writes++; await route.continue(); });
  let fail = true;
  await page.route('**/api/getinvestigationsnapshot', async route => {
    if (fail) { fail = false; await route.abort('failed'); } else await route.continue();
  });
  await page.getByRole('button', { name: '打开抽屉', exact: true }).click();
  await page.getByRole('button', { name: '重新读取视图' }).click();
  await expect(page.getByRole('button', { name: '抽屉中的信', exact: true })).toBeVisible();
  expect(writes).toBe(1);
});

test('backend unavailable shows a connection problem without local fake progress', async ({ page }) => {
  await page.route('**/api/**', route => route.abort('failed'));
  await page.goto('/');
  await expect(page.getByRole('button', { name: '重新连接' })).toBeVisible();
  await expect(page.getByRole('button', { name: '书桌抽屉', exact: true })).toHaveCount(0);
});
