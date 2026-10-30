import { test, expect, type Page } from '@playwright/test';
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200); return response.json();
}
async function waitForTime(page: Page, minutes: number, label: string) {
  await page.getByRole('region', { name: '案件时钟', exact: true }).getByRole('button', { name: `整理并等待${minutes}分钟`, exact: true }).click();
  await expect(page.getByRole('region', { name: '案件时钟', exact: true })).toContainText(label);
}
async function send(page: Page, question: string) {
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  return page.getByRole('log', { name: '自由问询记录', exact: true });
}

test('public map shows connected distances and route estimates; browsing and refresh consume no story time', async ({ page }) => {
  await page.goto('/map');
  await expect(page.getByRole('region', { name: '步行路线估算', exact: true })).toContainText('1060 米');
  const initial = await snapshot(page);
  expect(initial.caseRevision.value).toBe(12);
  expect(initial.clock.elapsedSeconds).toBe(0);
  expect(initial.areaMap.locations).toHaveLength(15);
  expect(JSON.stringify(initial)).not.toContain('clue_gun_verification');
  await page.getByLabel('终点', { exact: true }).selectOption('blue_boar');
  await expect(page.getByRole('region', { name: '步行路线估算', exact: true })).toContainText('450 米，步行约 6 分钟');
  await page.getByRole('button', { name: '地图地点：下农场', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('region', { name: '步行路线估算', exact: true })).toContainText('3100 米');
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: 'test-results/map-mobile.png', fullPage: true });
  await go(page, '案件简介'); await go(page, '地点地图'); await page.reload();
  await expect(page.getByRole('region', { name: '步行路线估算', exact: true })).toBeVisible();
  expect(await snapshot(page)).toEqual(initial);
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.screenshot({ path: 'test-results/village-map.png', fullPage: true });
});

test('timed checks finish, survive request and wait response loss, and persist exact time on reload', async ({ page }) => {
  await page.goto('/police');
  await expect(page.getByRole('region', { name: '核查进展', exact: true })).toBeVisible();
  const applications: string[] = []; let dropApplication = true;
  await page.route('**/api/requestexternalcheck', async route => {
    if (route.request().postDataJSON().check !== 'medical_timing') { await route.continue(); return; }
    applications.push(route.request().postData()!);
    if (dropApplication) { dropApplication = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  await page.getByRole('button', { name: '申请核实医生到场与初查记录', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('region', { name: '案件时钟', exact: true })).toContainText('09:02');
  expect(applications).toHaveLength(2); expect(applications[0]).toBe(applications[1]);
  await page.getByRole('button', { name: '申请核验手枪与弹头', exact: true }).click();
  await expect(page.getByRole('region', { name: '案件时钟', exact: true })).toContainText('09:04');
  await waitForTime(page, 5, '09:09');
  let state = await snapshot(page);
  expect(state.clues).toHaveLength(0);
  await waitForTime(page, 5, '09:14');
  await expect(page.getByRole('article', { name: '医生到场与初查记录', exact: true })).toContainText('已完成');
  await expect(page.getByRole('article', { name: '手枪与弹头核验', exact: true })).toContainText('办理中');
  const waits: string[] = []; let dropWait = true;
  await page.route('**/api/advancetime', async route => {
    waits.push(route.request().postData()!);
    if (dropWait) { dropWait = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  await page.getByRole('button', { name: '整理并等待15分钟', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('region', { name: '案件时钟', exact: true })).toContainText('09:29');
  expect(waits).toHaveLength(2); expect(waits[0]).toBe(waits[1]);
  await expect(page.getByRole('article', { name: '手枪与弹头核验', exact: true })).toContainText('已完成');
  state = await snapshot(page);
  expect(state.clock.elapsedSeconds).toBe(1740);
  expect(state.clues).toHaveLength(2);
  await go(page, '线索整理');
  await expect(page.getByRole('region', { name: '线索笔记', exact: true })).toContainText('约19:00');
  await expect(page.getByRole('region', { name: '线索笔记', exact: true })).toContainText('.25');
  await page.reload(); await expect(page.getByRole('heading', { name: '线索整理', exact: true })).toBeVisible();
  expect(await snapshot(page)).toEqual(state);
  await page.context().storageState({ path: '.build-cache/map-clock-state.json' });
  await page.screenshot({ path: 'test-results/check-results.png', fullPage: true });
});

test('police model answers from pending state and then actual completed results without being shown future reports', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses local model fixture.');
  await page.goto('/police');
  await page.getByRole('button', { name: '申请核验手枪与弹头', exact: true }).click();
  await expect(await send(page, '手枪核验还要多久？')).toContainText('预计周五 09:20');
  const before = await snapshot(page);
  expect(before.clues.some((c: { id: string }) => c.id === 'clue_gun_verification')).toBe(false);
  await waitForTime(page, 15, '09:19');
  await waitForTime(page, 5, '09:24');
  await expect(await send(page, '请说明手枪核验结果。')).toContainText('两者口径相符');
  const after = await snapshot(page);
  expect(after.interviews.at(-1).mode).toBe('answered');
  expect(after.interviews.at(-1).shownClues).toEqual([]);
  expect(after.clues.some((c: { id: string }) => c.id === 'clue_gun_verification')).toBe(true);
});
