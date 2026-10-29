import { test, expect, type Page } from '@playwright/test';
test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Requires the local model fixture.');
async function send(page: Page, question: string) {
  const panel = page.getByRole('region', { name: '自由问询', exact: true });
  await panel.getByLabel('你的问题', { exact: true }).fill(question);
  await panel.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(panel.getByLabel('你的问题', { exact: true })).toHaveValue('');
  return page.getByRole('log', { name: '自由问询记录', exact: true });
}
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  return response.json();
}

test('case introduction presents public relationships and leads into both new interviews', async ({ page }) => {
  await page.goto('/case');
  const cast = page.getByRole('region', { name: '案中人物', exact: true });
  await expect(cast.getByRole('article')).toHaveCount(13);
  await expect(cast.getByRole('article', { name: '安妮·普罗瑟罗', exact: true })).toContainText('死者的妻子');
  await expect(cast.getByRole('article', { name: '劳伦斯·雷丁', exact: true })).toContainText('前夜已自首');
  await expect(cast).not.toContainText('18:45');
  await expect(cast).not.toContainText('共谋');
  const initial = await snapshot(page);
  expect(initial.clues).toHaveLength(0);
  expect(JSON.stringify(initial)).not.toContain('privateContext');
  await page.screenshot({ path: 'test-results/case-public-relationships.png', fullPage: true });
  await cast.getByRole('link', { name: '询问劳伦斯·雷丁', exact: true }).click();
  await expect(page.getByRole('region', { name: '自由问询', exact: true })).toContainText('向劳伦斯·雷丁提问');
  await expect(await send(page, '你来寓所的经过是什么？')).toContainText('六点三刻');
  await go(page, '案件简介');
  await page.getByRole('link', { name: '询问马普尔小姐', exact: true }).click();
  await expect(await send(page, '你昨天看见哪些人来过？')).toContainText('转过屋角');
  const saved = await snapshot(page);
  expect(saved.clues.some((clue: { id: string }) => clue.id === 'clue_redding_account')).toBe(true);
  expect(saved.clues.some((clue: { id: string }) => clue.id === 'clue_marple_visitors')).toBe(true);
  expect(saved.stage.completed).toBe(false);
  await page.reload();
  await page.getByRole('button', { name: '与马普尔小姐交谈', exact: true }).click();
  await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('转过屋角');
  await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).not.toContainText('我就开枪杀了他');
  expect(await snapshot(page)).toEqual(saved);
  await page.screenshot({ path: 'test-results/marple-conversation.png', fullPage: true });
});

test('showing evidence enables follow-ups and stores the response without forcing the story forward', async ({ page }) => {
  await page.goto('/scene');
  await page.getByText('物品列表（备用入口）', { exact: true }).click();
  await page.locator('details').getByRole('button', { name: '座钟', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '座钟操作', exact: true })).toContainText('18:22');
  await page.keyboard.press('Escape');
  await go(page, '警方交流');
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '询问医生的初查意见', exact: true }).click();
  await expect(page.getByRole('heading', { name: '医生初查：死亡时间', exact: true })).toBeVisible();
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  const panel = page.getByRole('region', { name: '自由问询', exact: true });
  await panel.getByRole('checkbox', { name: '座钟读数', exact: true }).check();
  await expect(await send(page, '请看座钟记录，你动过它吗？')).toContainText('我动过它');
  await panel.getByRole('checkbox', { name: '医生初查：死亡时间', exact: true }).check();
  await expect(await send(page, '医生的时间意见与你的说法怎样核对？')).toContainText('六点三刻');
  let saved = await snapshot(page);
  expect(saved.clues.some((clue: { id: string }) => clue.id === 'clue_redding_medical_reply')).toBe(true);
  await page.getByRole('button', { name: '与马普尔小姐交谈', exact: true }).click();
  await panel.getByRole('checkbox', { name: '雷丁的首份供述', exact: true }).check();
  await expect(await send(page, '请看雷丁的供述，你能核实什么？')).toContainText('没有看见');
  saved = await snapshot(page);
  expect(saved.clues.some((clue: { id: string }) => clue.id === 'clue_marple_account_reply')).toBe(true);
  expect(saved.stage.completed).toBe(false);
  expect(JSON.stringify(saved)).not.toContain('privateContext');
  await page.screenshot({ path: 'test-results/redding-evidence-followup.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  for (const name of ['案件简介', '人物档案', '人物问询']) {
    await go(page, name);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
});
