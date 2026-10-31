import { test, expect, type Page } from './support/investigation-test';

const sessionKey = 'detective-scenic:server-session:v6';
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}
async function snapshot(page: Page) {
  const stored = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!), sessionKey);
  const response = await page.request.post('/api/getinvestigationsnapshot', {
    data: { sessionId: stored.sessionId }, headers: { 'X-Detective-Client': 'web' },
  });
  expect(response.status()).toBe(200);
  return response.json();
}

test('case briefing and public profiles lead into the same investigation without disclosing answers', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/case');
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '调查导航' }).getByRole('link')).toHaveCount(9);
  await expect(page.getByRole('region', { name: '阶段目标', exact: true })).toContainText('雷丁已向警方自首');
  await expect(page.locator('canvas')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '发送问题' })).toHaveCount(0);
  const initial = await snapshot(page);
  expect(initial.sequence.value).toBe(0);
  await expect(page.getByText('18:07', { exact: false })).toHaveCount(0);
  await page.getByRole('link', { name: '查看人物档案', exact: true }).click();
  await expect(page.getByRole('heading', { name: '人物档案', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '克莱门特牧师', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '玛丽', exact: true })).toBeVisible();
  await expect(page.getByText('还没有记录这位人物的证词。可以先与其交谈。', { exact: true })).toHaveCount(5);
  await expect(page.getByText('拨快一刻钟', { exact: false })).toHaveCount(0);
  await page.getByRole('link', { name: '询问玛丽', exact: true }).click();
  await expect(page.getByRole('region', { name: '自由问询', exact: true })).toContainText('向玛丽提问');
  await go(page, '案件简介');
  await page.getByRole('link', { name: '开始现场调查', exact: true }).click();
  await expect(page.getByRole('heading', { name: '现场探索', exact: true })).toBeVisible();
  expect(await snapshot(page)).toEqual(initial);
  await go(page, '调查日志');
  await expect(page.getByRole('region', { name: '调查记录', exact: true })).toContainText('还没有调查记录');
  expect(await snapshot(page)).toEqual(initial);
  for (const [path, title] of [['/case', '案件简介'], ['/people', '人物档案'], ['/journal', '调查日志']]) {
    await page.goto(path!);
    await expect(page.getByRole('heading', { name: title!, exact: true })).toBeVisible();
    expect((await snapshot(page)).sessionId).toBe(initial.sessionId);
  }
  await page.goBack();
  await expect(page.getByRole('heading', { name: '人物档案', exact: true })).toBeVisible();
  await page.goForward();
  await expect(page.getByRole('heading', { name: '调查日志', exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(errors).toEqual([]);
});

test('profiles and searchable journal reflect only saved records and survive a refresh', async ({ page }) => {
  await page.goto('/scene');
  await page.getByText('物品列表（备用入口）', { exact: true }).click();
  await page.locator('details').getByRole('button', { name: '抽屉', exact: true }).click();
  const drawer = page.getByRole('dialog', { name: '抽屉操作', exact: true });
  await drawer.getByRole('button', { name: '打开抽屉', exact: true }).click();
  await drawer.getByRole('button', { name: '推回抽屉', exact: true }).click();
  await expect(drawer.getByRole('button', { name: '打开抽屉', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.locator('details').getByRole('button', { name: '座钟', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '座钟操作', exact: true })).toContainText('18:22');
  await page.keyboard.press('Escape');
  await go(page, '人物档案');
  await page.getByRole('link', { name: '询问克莱门特牧师', exact: true }).click();
  await expect(page.getByRole('region', { name: '自由问询', exact: true })).toContainText('向克莱门特牧师提问');
  await page.getByText('固定问答与材料笔记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '询问：座钟平时准吗？', exact: true }).click();
  await expect(page.getByRole('button', { name: '询问：座钟平时准吗？', exact: true })).toHaveCount(0);
  const saved = await snapshot(page);
  expect(saved.sequence.value).toBe(4);
  await go(page, '人物档案');
  const clement = page.getByRole('article', { name: '克莱门特牧师', exact: true });
  await expect(clement).toContainText('已记录材料 1 份');
  await clement.locator('summary').click();
  await expect(clement).toContainText('拨快一刻钟');
  await expect(page.getByRole('article', { name: '玛丽', exact: true })).not.toContainText('拨快一刻钟');
  await page.screenshot({ path: 'test-results/pages-people.png', fullPage: true });
  await go(page, '调查日志');
  const records = page.getByRole('region', { name: '调查记录', exact: true }).getByRole('listitem');
  await expect(records).toHaveCount(4);
  await expect(records.first()).toContainText('打开抽屉');
  await page.getByLabel('排列顺序', { exact: true }).selectOption('newest');
  await expect(records.first()).toContainText('拨快一刻钟');
  await page.getByLabel('只看有新材料的记录', { exact: true }).check();
  await expect(records).toHaveCount(2);
  await page.getByRole('searchbox', { name: '搜索记录', exact: true }).fill('牧师谈座钟');
  await expect(records).toHaveCount(1);
  await expect(records.first()).toContainText('新记录材料：牧师谈座钟');
  await page.getByRole('searchbox', { name: '搜索记录', exact: true }).fill('没有这个调查记录');
  await expect(records).toHaveCount(0);
  await expect(page.getByRole('region', { name: '调查记录', exact: true })).toContainText('没有符合条件的记录');
  expect(await snapshot(page)).toEqual(saved);
  await page.reload();
  await expect(records).toHaveCount(4);
  expect(await snapshot(page)).toEqual(saved);
  await page.screenshot({ path: 'test-results/pages-journal.png', fullPage: true });
  await page.getByRole('link', { name: '前往线索整理查看全部材料', exact: true }).click();
  await expect(page.getByRole('heading', { name: '线索整理', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '调查记录', exact: true })).toHaveCount(0);
  await page.getByRole('link', { name: '查看调查日志（4条）', exact: true }).click();
  await expect(records).toHaveCount(4);
  await go(page, '案件简介');
  await expect(page.getByRole('link', { name: '返回现场调查', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '这次调查', exact: true })).toContainText('4');
  await page.screenshot({ path: 'test-results/pages-case.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  for (const name of ['案件简介', '人物档案', '调查日志']) {
    await go(page, name);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
});
