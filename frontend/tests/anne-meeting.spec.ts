import { test, expect, type Page } from '@playwright/test';
test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses the local model fixture.');
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
async function send(page: Page, question: string) {
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  return page.getByRole('log', { name: '自由问询记录', exact: true });
}
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  return response.json();
}

test('explicit police meeting unlocks Anne without automatically creating her statement; lost reply retries once', async ({ page }) => {
  await page.goto('/case');
  await expect(page.getByRole('region', { name: '案中人物', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '询问安妮·普罗瑟罗', exact: true })).toHaveCount(0);
  let state = await snapshot(page);
  expect(state.witnesses.some((w: { id: string }) => w.id === 'anne')).toBe(false);
  await go(page, '警方交流');
  await expect(page.getByRole('button', { name: '安排与安妮正式会面', exact: true })).toHaveCount(0);
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await expect(await send(page, '你来寓所的经过是什么？')).toContainText('六点三刻');
  await go(page, '警方交流');
  const payloads: string[] = [];
  let drop = true;
  await page.route('**/api/executeoperation', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.getByRole('button', { name: '安排与安妮正式会面', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(payloads).toHaveLength(2);
  expect(payloads[0]).toBe(payloads[1]);
  state = await snapshot(page);
  expect(state.sequence.value).toBe(2);
  expect(state.witnesses.some((w: { id: string }) => w.id === 'anne')).toBe(true);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_anne_statement')).toBe(false);
  await page.getByRole('link', { name: '前往人物问询，与安妮交谈', exact: true }).click();
  await page.getByRole('button', { name: '与安妮·普罗瑟罗交谈', exact: true }).click();
  await expect(await send(page, '你想向警方说明什么？')).toContainText('是我杀了丈夫');
  const saved = await snapshot(page);
  expect(saved.clues.some((c: { id: string }) => c.id === 'clue_anne_statement')).toBe(true);
  expect(saved.stage.completed).toBe(false);
  await page.reload();
  await page.getByRole('button', { name: '与安妮·普罗瑟罗交谈', exact: true }).click();
  await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('是我杀了丈夫');
  expect(await snapshot(page)).toEqual(saved);
  await page.context().storageState({ path: '.build-cache/anne-meeting-state.json' });
  await page.screenshot({ path: 'test-results/anne-meeting.png', fullPage: true });
});

test('police discuss two actual statements only after both are shown', async ({ page }) => {
  await page.goto('/interviews');
  await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await send(page, '你来寓所的经过是什么？');
  await go(page, '警方交流');
  await page.getByRole('button', { name: '安排与安妮正式会面', exact: true }).click();
  await expect(page.getByRole('link', { name: '前往人物问询，与安妮交谈', exact: true })).toBeVisible();
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与安妮·普罗瑟罗交谈', exact: true }).click();
  await send(page, '你想向警方说明什么？');
  await go(page, '警方交流');
  const panel = page.getByRole('region', { name: '自由问询', exact: true });
  for (const name of ['雷丁的首份供述', '安妮的认罪陈述']) await panel.getByRole('checkbox', { name, exact: true }).check();
  await expect(await send(page, '这两份供述还有哪些地方需要核查？')).toContainText('仍待独立核查');
  const state = await snapshot(page);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_two_statements')).toBe(true);
  expect(state.police.sharedClues.sort()).toEqual(['clue_anne_statement', 'clue_redding_account']);
  expect(state.stage.completed).toBe(false);
  await page.screenshot({ path: 'test-results/two-statements-police.png', fullPage: true });
});
