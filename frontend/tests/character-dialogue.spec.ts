import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const headers = { 'X-Detective-Client': 'web' };
const key = 'detective-scenic:server-session:v6';
async function post(page: Page, name: string, data: object = {}) {
  const response = await page.request.post('/api/' + name, { headers, data });
  expect(response.status()).toBe(200); return response.json();
}
async function start(page: Page) {
  await post(page, 'openlocalidentity'); const sessionId = randomUUID();
  await post(page, 'openinvestigation', { sessionId, caseId: 'vicarage_study', revision: { value: 23 } });
  const snapshot = () => post(page, 'getinvestigationsnapshot', { sessionId });
  async function change(name: string, payload: object) {
    const state = await snapshot(); await post(page, name, { sessionId, requestId: randomUUID(), expectedSequence: state.sequence, ...payload });
  }
  async function read(route: string) {
    const state = await snapshot();
    const point = state.points.find((p: { routes: { id: string }[] }) => p.routes.some(r => r.id === route));
    expect(point, route).toBeDefined(); await change('investigate', { point: point.id, route });
  }
  await page.addInitScript(({ key, sessionId }) => { if (!localStorage.getItem(key)) localStorage.setItem(key, JSON.stringify({ version: 1, sessionId, creationPending: false, pending: null })); }, { key, sessionId });
  return { snapshot, change, read };
}
test('a saved counterquestion waits for the player and restores without acquiring a new statement', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'UI protocol fixture; real psychology is checked separately.');
  const game = await start(page); await game.read('route_redding_account'); await game.read('route_redding_gun');
  const before = await game.snapshot();
  await page.goto('/interviews'); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await page.getByLabel('你的问题', { exact: true }).fill('协议对话：我想继续谈，你要先确认什么？');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  const log = page.getByRole('log', { name: '自由问询记录', exact: true });
  await expect(log).toContainText('你们为什么怀疑安妮？');
  expect((await game.snapshot()).clues).toEqual(before.clues);
  await page.reload(); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await expect(log).toContainText('你们为什么怀疑安妮？');
  await page.getByLabel('你的问题', { exact: true }).fill('协议对话：没有警方结论，只是我自己的判断。');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(log).toContainText('只是你的判断？那我先前说的没有什么要改的。');
  expect((await game.snapshot()).clues).toEqual(before.clues);
  await page.context().storageState({ path: '.build-cache/character-dialogue-browser-state.json' });
});
test('revision23 fixed fallback needs actual challenges and still reaches Archer without a phase button', async ({ page }) => {
  const game = await start(page);
  await game.read('route_redding_account'); await game.read('route_redding_gun');
  let state = await game.snapshot();
  expect(state.points.flatMap((p: { routes: { id: string }[] }) => p.routes).some((r: { id: string }) => r.id === 'route_redding_gun_revised')).toBe(false);
  await game.change('executeoperation', { operation: 'open_anne_meeting' }); await game.read('route_anne_statement');
  state = await game.snapshot();
  expect(state.points.flatMap((p: { routes: { id: string }[] }) => p.routes).some((r: { id: string }) => r.id === 'route_redding_revised')).toBe(false);
  await game.change('requestexternalcheck', { check: 'medical_timing' });
  await game.change('executeoperation', { operation: 'open_movement_meetings' });
  await game.read('route_stone_walk'); await game.read('route_stone_inn'); await game.change('advancetime', { minutes: 15 });
  for (const route of ['route_redding_revised', 'route_redding_gun_revised', 'route_anne_revised', 'route_police_revised_review']) await game.read(route);
  await game.change('executeoperation', { operation: 'open_archer_meeting' });
  await game.read('route_archer_gun_memory'); await game.read('route_archer_household'); await game.read('route_police_archer_review');
  state = await game.snapshot(); expect(state.followUps).toEqual([]); expect(state.caseRevision.value).toBe(23);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_redding_gun')).toBe(true);
  expect(state.clues.some((c: { id: string }) => c.id === 'clue_police_archer_review')).toBe(true);
  await page.goto('/interviews'); await page.getByRole('button', { name: '与阿彻太太交谈', exact: true }).click();
  await expect(page.getByRole('heading', { name: '向阿彻太太提问', exact: true })).toBeVisible();
  await page.context().storageState({ path: '.build-cache/character-fallback-browser-state.json' });
});
