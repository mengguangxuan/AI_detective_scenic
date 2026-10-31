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
async function waitForTime(page: Page, minutes: number) {
  const before = (await snapshot(page)).clock.elapsedSeconds;
  await page.getByRole('button', { name: `整理并等待${minutes}分钟`, exact: true }).click();
  await expect.poll(async () => (await snapshot(page)).clock.elapsedSeconds).toBe(before + minutes * 60);
}

test('independent woodland route collects a sample, obtains a tentative opinion and preserves original hearings', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/police');
  await expect(page.getByRole('heading', { name: '警方交流', exact: true })).toBeVisible();
  expect((await snapshot(page)).caseRevision.value).toBe(21);
  await expect(page.getByRole('button', { name: '申请查访树林声响附近现场', exact: true })).toBeDisabled();
  await expect(page.getByRole('region', { name: '核查进展', exact: true })).not.toContainText('苦味酸');
  await fixedPerson(page, '玛丽', '询问：树林方向那声响是什么时候？');
  await fixedPerson(page, '玛丽', '追问：当时还有什么事帮你记得时间？');
  await fixedPerson(page, '马普尔小姐', '询问：有没有听见声响？');
  await go(page, '警方交流');
  await expect(page.getByRole('button', { name: '申请查访树林声响附近现场', exact: true })).toBeDisabled();
  await fixedPolice(page, '出示玛丽与马普尔的响声回忆');
  const original = (await snapshot(page)).clues;
  await expect(page.getByRole('button', { name: '申请请海多克辨认树林样本', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '申请查访树林声响附近现场', exact: true }).click();
  await expect(page.getByRole('article', { name: '树林声响现场查访', exact: true })).toContainText('办理中');
  await waitForTime(page, 5);
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_woodland_sample')).toBe(false);
  await page.reload();
  await expect(page.getByRole('article', { name: '树林声响现场查访', exact: true })).toContainText('办理中');
  await waitForTime(page, 15);
  await expect(page.getByRole('article', { name: '树林声响现场查访', exact: true })).toContainText('已完成');
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_crystal_opinion')).toBe(false);
  await page.getByRole('button', { name: '申请请海多克辨认树林样本', exact: true }).click();
  await expect(page.getByRole('article', { name: '树林样本初步辨认', exact: true })).toContainText('办理中');
  await waitForTime(page, 5);
  expect((await snapshot(page)).clues.some((c: { id: string }) => c.id === 'clue_crystal_opinion')).toBe(false);
  await waitForTime(page, 5);
  await fixedPolice(page, '出示响声回忆、树林样本记录与医生意见');
  const state = await snapshot(page);
  expect(state.clues).toHaveLength(6);
  expect(state.clues.filter((c: { id: string }) => original.some((o: { id: string }) => o.id === c.id))).toEqual(original);
  expect(state.clues.find((c: { id: string }) => c.id === 'clue_crystal_opinion').text).toContain('并非化验确认');
  expect(state.police.sharedClues).toEqual(expect.arrayContaining(['clue_woodland_sample', 'clue_crystal_opinion']));
  expect(state.stage.completed).toBe(false);
  await page.reload(); expect(await snapshot(page)).toEqual(state);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: '.build-cache/woodland-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
  await page.context().storageState({ path: '.build-cache/woodland-state.json' });
  writeFileSync('.build-cache/woodland-snapshot.json', JSON.stringify(state));
});

test.describe('actual previous revision compatibility', () => {
  test.use({ storageState: '.build-cache/pre-woodland-v14-state.json' });
  test('revision 14 retains completed calls without new woodland checks', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('status').filter({ hasText: '已恢复原修订的调查' })).toContainText('原调查仍保留在服务器');
    const state = await snapshot(page);
    expect(state.caseRevision.value).toBe(14);
    expect(state.externalChecks).toHaveLength(7);
    expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_call_review')).toBe(true);
    await expect(page.getByRole('button', { name: '申请查访树林声响附近现场', exact: true })).toHaveCount(0);
    await page.reload(); expect(await snapshot(page)).toEqual(state);
  });
});
