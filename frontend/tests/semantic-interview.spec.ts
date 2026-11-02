import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
const headers = { 'X-Detective-Client': 'web' };
const key = 'detective-scenic:server-session:v6';
async function snapshot(page: Page) {
  const sessionId = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).sessionId, key);
  const response = await page.request.post('/api/getinvestigationsnapshot', { headers, data: { sessionId } });
  expect(response.status()).toBe(200); return response.json();
}
async function begin(page: Page) {
  await page.goto('/cases/vicarage_study');
  await page.getByRole('button', { name: '开始新调查', exact: true }).click();
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  expect((await snapshot(page)).caseRevision.value).toBe(26);
}
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
test('revision26 fixed questions use the same per-claim records without a model', async ({ page }) => {
  await begin(page); await go(page, '人物问询');
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  await page.getByText('固定问答与材料笔记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '询问：座钟平时准吗？', exact: true }).click();
  await expect(page.getByLabel('本轮调查记录', { exact: true })).toContainText('已记录：牧师谈座钟');
  const state = await snapshot(page);
  expect(state.clues.map((c: { id: string }) => c.id)).toEqual(['clue_habit']);
  expect(state.interviews.at(-1).recognition.claims[0].text).toBe(state.clues[0].text);
});

test.describe('semantic workflow protocol', () => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Requires the local protocol model fixture.');
  test('review failure retains the exact question and reply across reload; retry only recognizes the saved speech', async ({ page }) => {
    await begin(page); await go(page, '人物问询');
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    const question = '协议语义：核对中断-' + randomUUID();
    await page.getByLabel('你的问题', { exact: true }).fill(question);
    await page.getByRole('button', { name: '发送问题', exact: true }).click();
    await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
    const pending = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).pending.command, key);
    const before = await snapshot(page); expect(before.clues).toEqual([]); expect(before.interviews).toEqual([]);
    let release: (() => void) | undefined;
    await page.route('**/api/askwitness', async route => {
      const response = await route.fetch();
      await new Promise<void>(resolve => { release = resolve; });
      await route.fulfill({ response });
    });
    await page.reload();
    await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
    await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toHaveCount(0);
    await expect.poll(() => release !== undefined).toBe(true);
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    release!();
    await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('我习惯把书房座钟拨快十五分钟。');
    await expect(page.getByLabel('本轮调查记录', { exact: true })).toContainText('牧师谈座钟');
    const after = await snapshot(page);
    expect(after.interviews).toHaveLength(1); expect(after.interviews[0].requestId).toBe(pending.requestId);
    const count = await page.request.get('http://127.0.0.1:8091/semantic-counts?question=' + encodeURIComponent(question));
    expect(await count.json()).toEqual({ actor: 1, review: 2 });
    await page.context().storageState({ path: '.build-cache/semantic-browser-state.json' });
    writeFileSync('.build-cache/semantic-browser-snapshot.json', JSON.stringify(after));
  });
  test('an invalid individual recognition item cannot discard valid speech or grant an unauthorized clue', async ({ page }) => {
    await begin(page); await go(page, '人物问询');
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    await page.getByLabel('你的问题', { exact: true }).fill('协议语义：混入登记');
    await page.getByRole('button', { name: '发送问题', exact: true }).click();
    await expect(page.getByLabel('本轮调查记录', { exact: true })).toContainText('部分记录尚未确认');
    const state = await snapshot(page);
    expect(state.clues.map((c: { id: string }) => c.id)).toEqual(['clue_habit']);
    expect(state.interviews.at(-1).recognition.unconfirmed).toBe(true);
    expect(state.interviews.at(-1).reply).toBe('我习惯把书房座钟拨快十五分钟。');
  });
  test('a time-only spoken answer does not also register the cause of death', async ({ page }) => {
    await begin(page); await go(page, '警方交流');
    await page.getByLabel('你的问题', { exact: true }).fill('协议语义：只说明死亡时间');
    await page.getByRole('button', { name: '发送问题', exact: true }).click();
    await expect(page.getByLabel('本轮调查记录', { exact: true })).toContainText('医生初查：死亡时间');
    expect((await snapshot(page)).clues.map((c: { id: string }) => c.id)).toEqual(['clue_medical_time']);
  });
});
