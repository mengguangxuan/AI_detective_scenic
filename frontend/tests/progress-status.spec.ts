import { writeFileSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200); return response.json();
}
async function go(page: Page, label: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: label, exact: true }).click();
}
async function fixed(page: Page, label: string) {
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  const detail = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  if (!(await detail.evaluate(n => (n as HTMLDetailsElement).open))) await detail.locator('summary').click();
  await detail.getByRole('button', { name: label, exact: true }).click();
  await expect(detail.getByRole('button', { name: label, exact: true })).toHaveCount(0);
}

test.describe('first-stage completion', () => {
  test.use({ storageState: '.build-cache/browser-restart-state.json' });
  test('gathering time evidence remains an investigation and does not become a case ending', async ({ page }) => {
    await page.goto('/case');
    await expect(page.getByRole('heading', { name: '调查目标', exact: true })).toBeVisible();
    await expect(page.getByLabel('调查状态', { exact: true })).toContainText('调查进行中');
    await expect(page.getByRole('region', { name: '调查进度', exact: true })).toHaveCount(0);
    const before = await snapshot(page);
    expect(before.stage.hasTimeComparison).toBe(false); expect(before.reasoning.ending).toBeNull();
    await go(page, '线索整理'); await page.reload();
    await expect(page.getByRole('heading', { name: '调查目标', exact: true })).toBeVisible();
    expect(await snapshot(page)).toEqual(before);
  });
});

test('a pending assessment cannot announce success; established and solved states follow saved results across every page', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses the local model protocol fixture.');
  test.setTimeout(60000);
  await page.goto('/case');
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  await fixed(page, '询问：你为什么离开寓所？');
  await go(page, '推理与复盘');
  const form = page.getByRole('form', { name: '推理评审', exact: true });
  for (const name of ['安妮·普罗瑟罗', '劳伦斯·雷丁', '牧师接到的电话']) await form.getByRole('checkbox', { name, exact: true }).check();
  await form.getByLabel('你的推理（人物责任、动机、经过与证据之间的联系）', { exact: true }).fill('协议验收：论证成立。此标记只用于本机协议夹具，验收收尾提示，不代替真实语义评审。');
  let release: () => void = () => {};
  const held = new Promise<void>(resolve => { release = resolve; });
  let received: () => void = () => {};
  const responseReady = new Promise<void>(resolve => { received = resolve; });
  await page.route('**/api/evaluatereasoning', async route => {
    const response = await route.fetch(); received(); await held; await route.fulfill({ response });
  });
  await form.getByRole('button', { name: '提交推理评审', exact: true }).click();
  await responseReady;
  try {
    await go(page, '案件简介');
    await expect(page.getByLabel('调查状态', { exact: true })).toContainText('调查进行中');
    await expect(page.getByRole('heading', { name: '核心推理已成立 · 待确认结案', exact: true })).toHaveCount(0);
  } finally { release(); }
  await expect(page.getByLabel('调查状态', { exact: true })).toContainText('推理已成立，待结案');
  const established = await snapshot(page);
  expect(established.reasoning.ending).toBeNull(); expect(JSON.stringify(established)).not.toContain('作者复盘');
  await expect(page.getByRole('heading', { name: '当前阶段 · 书房初查', exact: true })).toHaveCount(0);
  await page.reload();
  await page.getByRole('link', { name: '前往结案选择', exact: true }).click();
  await expect(page.getByRole('region', { name: '结案选择', exact: true })).toBeVisible();
  expect(await snapshot(page)).toEqual(established);
  await expect(page.getByRole('region', { name: '本案收尾与复盘', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '结案并查看复盘', exact: true }).click();
  await expect(page.getByLabel('调查状态', { exact: true })).toContainText('推理已结案');
  const closed = await snapshot(page); expect(closed.stage.completed).toBe(false);
  for (const label of ['案件简介', '地点地图', '现场探索', '线索整理', '人物档案', '人物问询', '警方交流', '调查日志', '推理与复盘']) {
    await go(page, label);
    await expect(page.getByLabel('调查状态', { exact: true })).toContainText('推理已结案');
    await expect(page.getByRole('heading', { name: '本次推理已结案', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: '当前阶段 · 书房初查', exact: true })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: '阶段完成 · 书房初查', exact: true })).toHaveCount(0);
  }
  expect(await snapshot(page)).toEqual(closed);
  await fixed(page, '询问：座钟平时准吗？');
  const later = await snapshot(page); expect(later.sequence.value).toBe(closed.sequence.value + 1);
  expect(later.reasoning.ending).toEqual(closed.reasoning.ending);
  await expect(page.getByLabel('调查状态', { exact: true })).toContainText('推理已结案');
  await page.getByRole('link', { name: '查看结案复盘', exact: true }).click();
  await expect(page.getByRole('region', { name: '本案收尾与复盘', exact: true })).toBeVisible();
  await page.reload(); expect(await snapshot(page)).toEqual(later);
  await page.context().storageState({ path: '.build-cache/progress-solved-state.json' });
  writeFileSync('.build-cache/progress-solved-snapshot.json', JSON.stringify(later));
});

test.describe('explicit reveal', () => {
  test.use({ storageState: '.build-cache/review-comparison-state.json' });
  test('revealing an answer stays distinct from solving and can be revisited without mutation', async ({ page }) => {
    await page.goto('/case');
    const banner = page.getByRole('region', { name: '调查进度', exact: true });
    await expect(banner).toContainText('本次未计为破案');
    await expect(page.getByLabel('调查状态', { exact: true })).toContainText('已查看解答');
    await expect(page.getByRole('heading', { name: '本次推理已结案', exact: true })).toHaveCount(0);
    const before = await snapshot(page);
    await page.setViewportSize({ width: 390, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    await banner.screenshot({ path: '.build-cache/progress-revealed-mobile.png' });
    await page.getByRole('link', { name: '回看作者解答', exact: true }).click();
    await expect(page.getByRole('region', { name: '本案收尾与复盘', exact: true })).toContainText('本次未计为破案');
    await page.reload(); expect(await snapshot(page)).toEqual(before);
  });
});

test.describe('historical one-shot submission', () => {
  test.use({ storageState: '.build-cache/conclusion-state.json' });
  test('legacy scores never become a semantic verdict or an automatic solved state', async ({ page }) => {
    await page.goto('/people');
    await expect(page.getByLabel('调查状态', { exact: true })).toContainText('历史推理已提交');
    const before = await snapshot(page);
    expect(before.caseRevision.value).toBe(16); expect(before.reasoning).toBeNull();
    await expect(page.getByRole('region', { name: '调查进度', exact: true })).toContainText('不表示文字推理已获评审通过');
    await page.getByRole('link', { name: '回看历史提交与复盘', exact: true }).click();
    await expect(page.getByRole('region', { name: '已保存的推理与复盘', exact: true })).toBeVisible();
    expect(await snapshot(page)).toEqual(before);
  });
});
