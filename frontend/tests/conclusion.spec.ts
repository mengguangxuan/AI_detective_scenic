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
async function obtainCall(page: Page) {
  await go(page, '人物问询');
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  await page.getByText('固定问答与材料笔记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '询问：你为什么离开寓所？', exact: true }).click();
  await expect(page.getByRole('button', { name: '询问：你为什么离开寓所？', exact: true })).toHaveCount(0);
  await go(page, '推理与复盘');
}

test('an explicit submission freezes the original reasoning, survives lost response retry, refresh and narrow screens', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/conclusion');
  await expect(page.getByRole('heading', { name: '推理与复盘', exact: true })).toBeVisible();
  const initial = await snapshot(page);
  expect(initial.caseRevision.value).toBe(16);
  expect(initial.conclusion).toBeNull();
  expect(JSON.stringify(initial)).not.toContain('共同策划杀害');
  expect(JSON.stringify(initial)).not.toContain('culprits');
  await expect(page.getByRole('region', { name: '已保存的推理与复盘' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '提交推理并查看复盘', exact: true })).toBeDisabled();
  await obtainCall(page);
  const form = page.getByRole('form', { name: '推理提交', exact: true });
  await form.getByRole('checkbox', { name: '玛丽', exact: true }).check();
  await form.getByRole('checkbox', { name: '牧师接到的电话', exact: true }).check();
  const explanation = '我想先保存自己的判断，并引用牧师的来电记录。'.repeat(20);
  await form.getByLabel('你的推理（动机、经过与材料之间的联系）', { exact: true }).fill(explanation);
  await go(page, '调查日志'); await go(page, '推理与复盘');
  await expect(form.getByLabel('你的推理（动机、经过与材料之间的联系）', { exact: true })).toHaveValue(explanation);
  await expect(form.getByRole('button', { name: '提交推理并查看复盘', exact: true })).toBeDisabled();
  await form.getByRole('checkbox', { name: '我已完成本次推理，同意显示完整复盘', exact: true }).check();
  const payloads: string[] = []; let drop = true;
  await page.route('**/api/submitconclusion', async route => {
    payloads.push(route.request().postData()!);
    if (drop) { drop = false; await route.fetch(); await route.abort('failed'); } else await route.continue();
  });
  const before = await snapshot(page);
  await form.getByRole('button', { name: '提交推理并查看复盘', exact: true }).click();
  await expect(page.getByRole('button', { name: '用原请求重试', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '用原请求重试', exact: true }).click();
  const result = page.getByRole('region', { name: '已保存的推理与复盘', exact: true });
  await expect(result).toContainText('本次推理已保存 · 0 / 100');
  await expect(result).toContainText(explanation);
  await expect(result).toContainText('作者复盘');
  expect(payloads).toHaveLength(2); expect(payloads[0]).toBe(payloads[1]);
  const state = await snapshot(page);
  expect(state.sequence.value).toBe(before.sequence.value + 1);
  expect(state.clues).toEqual(before.clues);
  expect(state.stage.completed).toBe(false);
  expect(state.conclusion.explanation).toBe(explanation);
  expect(state.police.sharedClues).toEqual(before.police.sharedClues);
  await page.reload(); expect(await snapshot(page)).toEqual(state);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: '.build-cache/conclusion-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
  await page.context().storageState({ path: '.build-cache/conclusion-state.json' });
  writeFileSync('.build-cache/conclusion-snapshot.json', JSON.stringify(state));
});

test('correct suspects do not claim sufficient evidence; the HTTP boundary rejects unearned references and oversized prose', async ({ page }) => {
  await page.goto('/conclusion');
  await expect(page.getByRole('heading', { name: '推理与复盘', exact: true })).toBeVisible();
  await obtainCall(page);
  const state = await snapshot(page);
  const headers = { 'X-Detective-Client': 'web' };
  const data = { sessionId: state.sessionId, expectedSequence: state.sequence, requestId: 'boundary', suspects: ['anne', 'redding'], evidence: ['clue_call'], explanation: '这里只提交一份来电陈述，尚未建立完整证据联系。' };
  const unearned = await page.request.post('/api/submitconclusion', { data: { ...data, evidence: ['clue_crystal_opinion'] }, headers });
  expect(unearned.status()).toBe(403);
  const oversized = await page.request.post('/api/submitconclusion', { data: { ...data, explanation: '推'.repeat(1001) }, headers });
  expect(oversized.status()).toBe(400);
  const forged = await page.request.post('/api/submitconclusion', { data: { ...data, suspectScore: 100 }, headers });
  expect(forged.status()).toBe(400);
  expect(await snapshot(page)).toEqual(state);
  const form = page.getByRole('form', { name: '推理提交', exact: true });
  await form.getByRole('checkbox', { name: '安妮·普罗瑟罗', exact: true }).check();
  await form.getByRole('checkbox', { name: '劳伦斯·雷丁', exact: true }).check();
  await form.getByRole('checkbox', { name: '牧师接到的电话', exact: true }).check();
  await form.getByLabel('你的推理（动机、经过与材料之间的联系）', { exact: true }).fill('推'.repeat(1000));
  await form.getByRole('checkbox', { name: '我已完成本次推理，同意显示完整复盘', exact: true }).check();
  await form.getByRole('button', { name: '提交推理并查看复盘', exact: true }).click();
  await expect(page.getByRole('region', { name: '已保存的推理与复盘', exact: true })).toContainText('本次推理已保存 · 50 / 100');
  await expect(page.getByRole('region', { name: '已保存的推理与复盘', exact: true })).toContainText('文字论证未自动判分');
  const saved = await snapshot(page);
  expect(saved.conclusion.evidenceScore).toBe(0);
  const again = await page.request.post('/api/submitconclusion', { data: { ...data, requestId: 'again', expectedSequence: saved.sequence }, headers });
  expect(again.status()).toBe(409);
  expect(await snapshot(page)).toEqual(saved);
});

test.describe('actual revision 15 compatibility', () => {
  test.use({ storageState: '.build-cache/pre-conclusion-v15-state.json' });
  test('woodland progress restores without receiving the new review or a migrated revision', async ({ page }) => {
    await page.goto('/conclusion');
    await expect(page.getByRole('heading', { name: '推理与复盘', exact: true })).toBeVisible();
    await expect(page.getByText('这份旧调查尚未开放推理提交，原进度仍可继续。体验本功能需另建当前修订的调查。', { exact: true })).toBeVisible();
    const state = await snapshot(page);
    expect(state.caseRevision.value).toBe(15);
    expect(state.conclusionCandidates).toEqual([]); expect(state.conclusion).toBeNull();
    expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_woodland_review')).toBe(true);
    await expect(page.getByRole('form', { name: '推理提交', exact: true })).toHaveCount(0);
    await page.reload(); expect(await snapshot(page)).toEqual(state);
  });
});
