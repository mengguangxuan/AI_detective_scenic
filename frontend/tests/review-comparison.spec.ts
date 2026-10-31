import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { test, expect, type Page } from './support/investigation-test';
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200); return response.json();
}
async function fixed(page: Page, label: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '人物问询', exact: true }).click();
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  const detail = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  if (!(await detail.evaluate(n => (n as HTMLDetailsElement).open))) await detail.locator('summary').click();
  await detail.getByRole('button', { name: label, exact: true }).click();
  await expect(detail.getByRole('button', { name: label, exact: true })).toHaveCount(0);
}
test('explicit reveal without an attempt freezes acquired observations, survives response loss and does not grow after later investigation', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  await page.goto('/conclusion');
  await expect(page.getByRole('heading', { name: '推理与复盘', exact: true })).toBeVisible();
  expect(JSON.stringify(await snapshot(page))).not.toContain('人物关系与共同动机');
  await fixed(page, '询问：你为什么离开寓所？');
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '推理与复盘', exact: true }).click();
  await page.getByText('提前查看作者解答（含剧透）', { exact: true }).click();
  await page.getByRole('checkbox', { name: '我选择放弃本次推理并查看完整解答', exact: true }).check();
  let count = 0;
  await page.route('**/api/revealreasoning', async route => {
    count++; const response = await route.fetch();
    if (count === 1) await route.abort('failed'); else await route.fulfill({ response });
  });
  await page.getByRole('button', { name: '放弃推理并查看解答', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  const panel = page.getByRole('region', { name: '本案收尾与复盘', exact: true });
  await expect(panel).toContainText('本次未计为破案');
  await expect(panel).toContainText('本局没有已提交的推理原文');
  const sections = page.getByRole('region', { name: '结案时的材料对照', exact: true });
  await expect(sections.getByRole('article')).toHaveCount(6);
  await expect(sections.getByText('牧师接到的电话 · 结案时已取得，末次未引用', { exact: true })).toBeVisible();
  const ended = await snapshot(page); expect(count).toBe(2);
  expect(ended.clues.map((c: { id: string }) => c.id)).toEqual(['clue_call']);
  await fixed(page, '询问：案发前安妮与雷丁有过什么来往？');
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '推理与复盘', exact: true }).click();
  const later = await snapshot(page);
  expect(later.reasoning.ending).toEqual(ended.reasoning.ending); expect(later.clues).toHaveLength(2);
  await expect(sections.getByText('牧师回忆的案发前感情谈话', { exact: false })).toHaveCount(0);
  await page.reload(); expect(await snapshot(page)).toEqual(later);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: '.build-cache/review-comparison-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
  await page.context().storageState({ path: '.build-cache/review-comparison-state.json' });
  writeFileSync('.build-cache/review-comparison-snapshot.json', JSON.stringify(later));
});
test.describe('actual revision 19 ending compatibility', () => {
  test.use({ storageState: '.build-cache/pre-review-v19-state.json' });
  test('real-model solved history keeps its exact original review and has no newly inserted sections', async ({ page }) => {
    await page.goto('/conclusion');
    const panel = page.getByRole('region', { name: '本案收尾与复盘', exact: true });
    await expect(panel).toContainText('已记录推理成立后的结案');
    await expect(page.getByRole('region', { name: '结案前最后一次推理', exact: true })).toBeVisible();
    const state = await snapshot(page);
    expect(state).toEqual(JSON.parse(readFileSync('.build-cache/pre-review-v19-snapshot.json', 'utf8')));
    expect(state.caseRevision.value).toBe(19); expect(state.reasoning.ending).not.toHaveProperty('sections');
    await expect(page.getByRole('region', { name: '结案时的材料对照', exact: true })).toHaveCount(0);
  });
});

const realSnapshot = '.build-cache/completion-live-solved-snapshot.json';
if (existsSync(realSnapshot) && JSON.parse(readFileSync(realSnapshot, 'utf8')).caseRevision.value === 20) test.describe('real TokenHub ending', () => {
  test.use({ storageState: '.build-cache/completion-live-solved-state.json' });
  test('the real supported reconstruction shows its original explanation and frozen source observations', async ({ page }) => {
    await page.goto('/conclusion');
    await expect(page.getByRole('region', { name: '结案前最后一次推理', exact: true })).toContainText('我认为安妮与雷丁共谋');
    const comparisons = page.getByRole('region', { name: '结案时的材料对照', exact: true });
    await expect(comparisons.getByRole('article')).toHaveCount(6);
    const call = comparisons.getByRole('article', { name: '假电话与牧师离开', exact: true });
    await call.getByText('牧师接到的电话 · 末次已引用', { exact: true }).click();
    await expect(call).toContainText('病人比前几天还好些');
    expect(await snapshot(page)).toEqual(JSON.parse(readFileSync(realSnapshot, 'utf8')));
    await page.screenshot({ path: '.build-cache/review-comparison-desktop.png', fullPage: true });
  });
});
