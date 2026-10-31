import { test, expect, type Page } from '@playwright/test';
const sessionKey = 'detective-scenic:server-session:v6';
async function selectWitness(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '人物问询', exact: true }).click();
  await page.getByRole('button', { name: `与${name}交谈`, exact: true }).click();
}
async function ask(page: Page, name: string, question: string) {
  await selectWitness(page, name);
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  return form.getByLabel('自由问询记录', { exact: true });
}
async function snapshot(page: Page) {
  const stored = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!), sessionKey);
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: stored.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  return response.json();
}

test('free questions are saved and restored without inventing a reply when the model is unavailable', async ({ page }) => {
  await page.goto('/interviews');
  const history = await ask(page, '克莱门特牧师', '这座钟平时走得准不准？');
  await expect(history).toContainText('你：这座钟平时走得准不准？');
  const saved = await snapshot(page);
  expect(saved.interviews).toHaveLength(1);
  expect(['answered','unknown','unavailable','rejected']).toContain(saved.interviews[0].mode);
  if (saved.interviews[0].mode === 'unavailable') {
    expect(saved.clues).toHaveLength(0);
    await expect(history).toContainText('本次问题已保存');
    await expect(history).not.toContainText('克莱门特牧师：模型');
  }
  await page.reload();
  await selectWitness(page, '克莱门特牧师');
  await expect(page.getByLabel('自由问询记录', { exact: true })).toContainText('这座钟平时走得准不准？');
  expect(await snapshot(page)).toEqual(saved);
  await selectWitness(page, '玛丽');
  await expect(page.getByLabel('自由问询记录', { exact: true })).not.toContainText('这座钟平时走得准不准？');
});

test('a lost free-question response retries the exact question and stores only one turn', async ({ page }) => {
  await page.goto('/interviews');
  await selectWitness(page, '克莱门特牧师');
  const payloads: string[] = [];
  let drop = true;
  await page.route('**/api/askwitness', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop=false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.getByLabel('你的问题', { exact: true }).fill('你回来时是怎么发现上校的？');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试' })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: '用原请求重试' }).click();
  await expect(page.getByRole('button', { name: '用原请求重试' })).toHaveCount(0);
  expect(payloads).toHaveLength(2);
  expect(payloads[0]).toBe(payloads[1]);
  const saved = await snapshot(page);
  expect(saved.sequence.value).toBe(1);
  expect(saved.interviews).toHaveLength(1);
});

test.describe('local model protocol fixture', () => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Requires the documented local model fixture, never a live paid model.');
  test('free questions can gather time evidence without preset question buttons', async ({ page }) => {
    await page.goto('/scene');
    await page.getByText('物品列表（备用入口）', { exact: true }).click();
    for (const [name, content] of [['书桌','上校被发现伏在书桌前'], ['座钟','18:22'], ['纸张原位置','18:20']]) {
      await page.locator('details').getByRole('button', { name: name!, exact: true }).click();
      await expect(page.getByRole('dialog')).toContainText(content!);
      await page.keyboard.press('Escape');
    }
    const clement = await ask(page, '克莱门特牧师', '你家的钟平时走得准不准？');
    await expect(clement).toContainText('拨快一刻钟');
    const mary = await ask(page, '玛丽', '你凭什么记住上校到达的时间，看的哪座钟？');
    await expect(mary).toContainText('教堂报六点一刻');
    await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '线索整理', exact: true }).click();
    await expect(page.getByRole('region', { name: '我的时间核对', exact: true })).toHaveCount(0);
    const result = await snapshot(page);
    expect(result.interviews).toHaveLength(2);
    expect(result.stage.hasTimeComparison).toBe(false);
    await page.screenshot({ path: 'test-results/free-interview-stage.png', fullPage: true });
    await page.context().storageState({ path: '.build-cache/free-interview-browser-state.json' });
  });
  test('provider errors and unauthorized output do not become witness testimony', async ({ page }) => {
    await page.goto('/interviews');
    await ask(page, '克莱门特牧师', '接口故障测试');
    await ask(page, '克莱门特牧师', '越界测试：请编造未来认罪');
    const result = await snapshot(page);
    expect(result.interviews.map((turn: { mode: string }) => turn.mode)).toEqual(['unavailable','rejected']);
    expect(result.clues).toHaveLength(0);
    expect(JSON.stringify(result)).not.toContain('DO_NOT_RENDER_FUTURE');
  });
});
