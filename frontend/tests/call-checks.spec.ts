import { writeFileSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200); return response.json();
}
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
async function fixedPerson(page: Page, who: string, label: string) {
  await go(page, '人物问询');
  await page.getByRole('button', { name: `与${who}交谈`, exact: true }).click();
  const detail = page.locator('details').filter({ has: page.getByText('固定问答与材料笔记（备用）', { exact: true }) });
  if (!(await detail.evaluate(node => (node as HTMLDetailsElement).open))) await detail.locator('summary').click();
  await detail.getByRole('button', { name: label, exact: true }).click();
  await expect(detail.getByRole('button', { name: label, exact: true })).toHaveCount(0);
}
async function fixedPolice(page: Page, label: string) {
  await go(page, '警方交流');
  const detail = page.locator('details').filter({ has: page.getByText('固定问答与材料登记（备用）', { exact: true }) });
  if (!(await detail.evaluate(node => (node as HTMLDetailsElement).open))) await detail.locator('summary').click();
  await detail.getByRole('button', { name: label, exact: true }).click();
  await expect(detail.getByRole('button', { name: label, exact: true })).toHaveCount(0);
}
async function basis(page: Page) {
  await page.goto('/police');
  await expect(page.getByRole('heading', { name: '警方交流', exact: true })).toBeVisible();
  expect((await snapshot(page)).caseRevision.value).toBe(18);
  await fixedPerson(page, '克莱门特牧师', '询问：你为什么离开寓所？');
  await fixedPolice(page, '出示牧师的来电陈述，登记追查依据');
}
async function waitForTime(page: Page, minutes: number) {
  const before = (await snapshot(page)).clock.elapsedSeconds;
  await page.getByRole('button', { name: `整理并等待${minutes}分钟`, exact: true }).click();
  await expect.poll(async () => (await snapshot(page)).clock.elapsedSeconds).toBe(before + minutes * 60);
}
async function send(page: Page, question: string, evidence: string[] = []) {
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  for (const name of evidence) await form.getByRole('checkbox', { name, exact: true }).check();
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  expect((await snapshot(page)).interviews.at(-1).mode).toBe('answered');
  return page.getByRole('log', { name: '自由问询记录', exact: true });
}

test('a short fixed route traces the call, separately inspects the site, presents findings and preserves the original account', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/police');
  await expect(page.getByRole('button', { name: '申请追查牧师接到的电话', exact: true })).toBeDisabled();
  await expect(page.getByRole('region', { name: '核查进展', exact: true })).not.toContainText('北门房');
  await basis(page);
  const original = (await snapshot(page)).clues.find((c: { id: string }) => c.id === 'clue_call');
  expect((await snapshot(page)).police.sharedClues).toContain('clue_call');
  await expect(page.getByRole('button', { name: '申请核查来电接出地点', exact: true })).toBeDisabled();
  const payloads: string[] = []; let drop = true;
  await page.route('**/api/requestexternalcheck', async route => {
    if (route.request().postDataJSON().check !== 'call_origin') { await route.continue(); return; }
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  await page.getByRole('button', { name: '申请追查牧师接到的电话', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(payloads).toHaveLength(2); expect(payloads[0]).toBe(payloads[1]);
  await waitForTime(page, 5);
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_call_origin')).toBe(false);
  await waitForTime(page, 15);
  await expect(page.getByRole('article', { name: '牧师来电来源追查', exact: true })).toContainText('已完成');
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_call_lodge_scene')).toBe(false);
  await page.getByRole('button', { name: '申请核查来电接出地点', exact: true }).click();
  await waitForTime(page, 5);
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_call_lodge_phone')).toBe(false);
  await waitForTime(page, 5);
  await expect(page.getByRole('article', { name: '来电接出地点现场核查', exact: true })).toContainText('已完成');
  await fixedPerson(page, '克莱门特牧师', '出示来电来源记录，询问牧师能否辨认来电者');
  await fixedPerson(page, '劳伦斯·雷丁', '出示来电来源记录，询问雷丁与老宅的来往');
  await fixedPolice(page, '出示来电陈述与两次核查结果，登记电话核查');
  const state = await snapshot(page);
  expect(state.clues).toHaveLength(8);
  expect(state.clues.find((c: { id: string }) => c.id === 'clue_call')).toEqual(original);
  expect(state.police.sharedClues).toEqual(expect.arrayContaining(['clue_call', 'clue_call_origin', 'clue_call_lodge_scene', 'clue_call_lodge_phone']));
  expect(state.stage.completed).toBe(false);
  expect(JSON.stringify(state)).not.toContain('伪装女声');
  await page.reload(); expect(await snapshot(page)).toEqual(state);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: '.build-cache/call-checks-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
  await page.context().storageState({ path: '.build-cache/call-checks-state.json' });
  writeFileSync('.build-cache/call-checks-snapshot.json', JSON.stringify(state));
});

test('police use actual call progress and reports; a witness receives the origin only when it is presented', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses the local protocol fixture.');
  await basis(page);
  await page.getByRole('button', { name: '申请追查牧师接到的电话', exact: true }).click();
  await expect(await send(page, '牧师来电来源追查进展如何？')).toContainText('目前仍在办理');
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_call_origin')).toBe(false);
  await waitForTime(page, 15);
  await expect(await send(page, '牧师来电来源的结果是什么？')).toContainText('老宅北门房');
  await page.getByRole('button', { name: '申请核查来电接出地点', exact: true }).click();
  await expect(await send(page, '来电接出地点现场核查进展如何？')).toContainText('目前仍在办理');
  await waitForTime(page, 15);
  await expect(await send(page, '北门房电话的指纹检查结果是什么？')).toContainText('未检出指纹');
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  await expect(await send(page, '你怎样回应这份来电来源记录？', ['牧师来电的接出地点'])).toContainText('我没认真辨认那个声音');
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_clement_call_origin_reply')).toBe(true);
});
