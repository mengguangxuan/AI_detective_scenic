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
async function paper(page: Page) {
  await page.goto('/scene');
  await page.getByText('物品列表（备用入口）', { exact: true }).click();
  await page.getByRole('button', { name: '纸张原位置', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '纸张原位置操作', exact: true })).toContainText('原件由警方保管');
  await page.keyboard.press('Escape');
  expect((await snapshot(page)).caseRevision.value).toBe(18);
  await go(page, '警方交流');
}
async function waitForTime(page: Page, minutes: number, label: string) {
  await page.getByRole('button', { name: `整理并等待${minutes}分钟`, exact: true }).click();
  await expect(page.getByRole('region', { name: '案件时钟', exact: true })).toContainText(label);
}
async function send(page: Page, question: string) {
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  return page.getByRole('log', { name: '自由问询记录', exact: true });
}

test('paper checks need the excerpt, finish separately, preserve it and retry a lost request before police registration', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/police');
  await expect(page.getByRole('button', { name: '申请检查纸张原件', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: '申请比对纸张笔迹', exact: true })).toBeDisabled();
  await paper(page);
  const excerpt = (await snapshot(page)).clues.find((c: { id: string }) => c.id === 'clue_paper');
  const payloads: string[] = []; let drop = true;
  await page.route('**/api/requestexternalcheck', async route => {
    if (route.request().postDataJSON().check !== 'paper_inspection') { await route.continue(); return; }
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  await page.getByRole('button', { name: '申请检查纸张原件', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
  expect(payloads).toHaveLength(2); expect(payloads[0]).toBe(payloads[1]);
  await expect(page.getByRole('article', { name: '纸张原件初检', exact: true })).toContainText('办理中');
  await expect(page.getByRole('button', { name: '申请比对纸张笔迹', exact: true })).toBeDisabled();
  await waitForTime(page, 5, '09:09');
  expect((await snapshot(page)).clues).toHaveLength(1);
  await waitForTime(page, 5, '09:14');
  let state = await snapshot(page);
  expect(state.clues).toHaveLength(3);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_paper_handwriting')).toBe(false);
  await page.getByRole('button', { name: '申请比对纸张笔迹', exact: true }).click();
  await waitForTime(page, 15, '09:31');
  expect((await snapshot(page)).clues).toHaveLength(3);
  await waitForTime(page, 5, '09:36');
  await go(page, '线索整理');
  const notes = page.getByRole('region', { name: '线索笔记', exact: true });
  await expect(notes).toContainText('正文不是普罗瑟罗上校所写');
  await expect(notes).toContainText('这张纸上没有检出指纹');
  await go(page, '警方交流');
  await page.getByText('固定问答与材料登记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '出示纸张摘录与检查结果，登记原件核查', exact: true }).click();
  await expect(page.getByRole('region', { name: '向警方出示材料', exact: true })).toContainText('已向警方出示 4 份');
  state = await snapshot(page);
  expect(state.clues).toHaveLength(5);
  expect(state.clues.find((c: { id: string }) => c.id === 'clue_paper')).toEqual(excerpt);
  expect(state.stage.completed).toBe(false);
  await page.reload(); expect(await snapshot(page)).toEqual(state);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: '.build-cache/paper-checks-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
  await page.context().storageState({ path: '.build-cache/paper-checks-state.json' });
  writeFileSync('.build-cache/paper-checks-snapshot.json', JSON.stringify(state));
});

test('police paper answers use pending state first and completed reports later', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses the local protocol fixture.');
  await paper(page);
  await page.getByRole('button', { name: '申请检查纸张原件', exact: true }).click();
  await expect(await send(page, '纸张原件检查进展如何？')).toContainText('目前仍在办理');
  await waitForTime(page, 15, '09:21');
  await expect(await send(page, '纸张原件的墨色检查结果是什么？')).toContainText('墨色与正文不同');
  await page.getByRole('button', { name: '申请比对纸张笔迹', exact: true }).click();
  await expect(await send(page, '纸张笔迹比对进展如何？')).toContainText('目前仍在办理');
  await waitForTime(page, 15, '09:42');
  await waitForTime(page, 5, '09:47');
  await expect(await send(page, '纸张笔迹比对结果是什么？')).toContainText('正文不是普罗瑟罗上校所写');
  expect((await snapshot(page)).interviews.every((t: { mode: string }) => t.mode === 'answered')).toBe(true);
});
