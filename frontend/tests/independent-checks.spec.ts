import { test, expect, type Page } from '@playwright/test';
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  return response.json();
}
async function send(page: Page, question: string) {
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  return page.getByRole('log', { name: '自由问询记录', exact: true });
}
async function choose(page: Page, name: string) {
  await page.getByRole('button', { name: `与${name}交谈`, exact: true }).click();
}
async function fixed(page: Page, label: string) {
  const detail = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  if (!(await detail.evaluate(node => (node as HTMLDetailsElement).open))) await detail.locator('summary').click();
  await detail.getByRole('button', { name: label, exact: true }).click();
  await expect(detail.getByRole('button', { name: label, exact: true })).toHaveCount(0);
}

test('independent witnesses are explicitly contacted, actually questioned and compared with police', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses local model fixture.');
  await page.goto('/case');
  await expect(page.getByRole('region', { name: '案中人物', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '询问斯通博士', exact: true })).toHaveCount(0);
  await go(page, '人物问询');
  await choose(page, '劳伦斯·雷丁'); await send(page, '你来寓所的经过是什么？');
  await go(page, '警方交流');
  await expect(page.getByRole('button', { name: '联系斯通和哈特内尔，核实行踪', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '安排与安妮正式会面', exact: true }).click();
  await go(page, '人物问询'); await choose(page, '安妮·普罗瑟罗');
  await send(page, '你想向警方说明什么？'); await send(page, '枪从哪里来？');
  await go(page, '警方交流');
  const payloads: string[] = [];
  let drop = true;
  await page.route('**/api/executeoperation', async route => {
    if (route.request().postDataJSON().operation !== 'open_movement_meetings') { await route.continue(); return; }
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.getByRole('button', { name: '联系斯通和哈特内尔，核实行踪', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(payloads).toHaveLength(2); expect(payloads[0]).toBe(payloads[1]);
  await page.getByRole('button', { name: '联系男仆，核查枪的来源', exact: true }).click();
  await expect(page.getByRole('region', { name: '已联系的核查对象', exact: true })).toContainText('斯通博士');
  let state = await snapshot(page);
  expect(state.caseRevision.value).toBe(19);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_valet_gun' || c.id === 'clue_stone_inn')).toBe(false);
  await page.getByRole('link', { name: '前往人物问询，核查证言', exact: true }).click();
  await choose(page, '上校的贴身男仆'); await expect(await send(page, '上校梳妆台的抽屉里有枪吗？')).toContainText('那里有的话我会看见');
  await choose(page, '斯通博士'); await send(page, '你何时遇见安妮和雷丁？');
  await expect(await send(page, '雷丁什么时候离开蓝野猪？')).toContainText('六点四十');
  await choose(page, '哈特内尔小姐'); await expect(await send(page, '安妮昨天傍晚来过吗？')).toContainText('园艺杂志');
  await go(page, '警方交流');
  const panel = page.getByRole('region', { name: '自由问询', exact: true });
  for (const name of ['安妮对枪来源的说法', '男仆对梳妆台抽屉的证言']) await panel.getByRole('checkbox', { name, exact: true }).check();
  await expect(await send(page, '请对照枪的来源说法。')).toContainText('两人的说法已分别保留');
  for (const name of ['雷丁的首份供述', '安妮的认罪陈述', '斯通所见：小巷与邮局旁', '斯通所见：雷丁离开蓝野猪', '哈特内尔所见：安妮来访']) await panel.getByRole('checkbox', { name, exact: true }).check();
  await expect(await send(page, '请对照行踪。')).toContainText('不能填补之前所有时段');
  state = await snapshot(page);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_movement_check')).toBe(true);
  expect(state.stage.completed).toBe(false);
  await page.reload();
  await expect(page.getByRole('region', { name: '已联系的核查对象', exact: true })).toContainText('上校的贴身男仆');
  expect(await snapshot(page)).toEqual(state);
  await page.context().storageState({ path: '.build-cache/independent-checks-state.json' });
  await page.screenshot({ path: 'test-results/independent-checks-police.png', fullPage: true });
});

test('sound recollections and police comparison remain playable through fixed questions', async ({ page }) => {
  await page.goto('/interviews'); await choose(page, '玛丽');
  await fixed(page, '询问：树林方向那声响是什么时候？');
  await fixed(page, '追问：当时还有什么事帮你记得时间？');
  await choose(page, '马普尔小姐');
  await fixed(page, '询问：有没有听见声响？');
  await go(page, '警方交流');
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '出示玛丽与马普尔的响声回忆', exact: true }).click();
  await expect(page.getByRole('region', { name: '向警方了解情况', exact: true })).toContainText('两人都没有看见声源');
  const saved = await snapshot(page);
  expect([...saved.police.sharedClues].sort()).toEqual(['clue_marple_sound', 'clue_mary_fish_time', 'clue_mary_sound']);
  expect(saved.clues.some((c: { id: string }) => c.id === 'clue_police_sound_check')).toBe(true);
  await page.reload(); await expect(page.getByRole('heading', { name: '警方交流', exact: true })).toBeVisible();
  expect(await snapshot(page)).toEqual(saved);
  await page.screenshot({ path: 'test-results/independent-checks-sound.png', fullPage: true });
});
