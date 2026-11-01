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
test('a short answer uses role history after refresh and another witness starts separately', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Role-history protocol fixture.');
  const game = await start(page); const before = await game.snapshot();
  await page.goto('/interviews'); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await page.getByLabel('你的问题', { exact: true }).fill('协议接话：先问我');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  const log = page.getByRole('log', { name: '自由问询记录', exact: true });
  await expect(log).toContainText('你是说抽屉里的那支枪吗？');
  await page.reload(); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await page.getByLabel('你的问题', { exact: true }).fill('对');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(log).toContainText('明白，你指的是抽屉里的那支枪。');
  await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
  await page.getByLabel('你的问题', { exact: true }).fill('协议接话：另一人物');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(log).toContainText('这是我们这次的第一句交谈。');
  expect((await game.snapshot()).clues).toEqual(before.clues);
  await page.context().storageState({ path: '.build-cache/continuity-browser-state.json' });
});

test('an ambiguous follow-up can clarify and the saved question clears when answered', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Focus-state protocol fixture.');
  const game = await start(page); const before = await game.snapshot();
  await page.goto('/interviews'); await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
  const send = async (question: string) => {
    await page.getByLabel('你的问题', { exact: true }).fill(question);
    await page.getByRole('button', { name: '发送问题', exact: true }).click();
  };
  const log = page.getByRole('log', { name: '自由问询记录', exact: true });
  await send('协议焦点：说明上一句'); await expect(log).toContainText('牧师让我去请医生，我便出门了。');
  await send('为什么？'); await expect(log).toContainText('你是问他为什么让我去请医生吗？');
  expect((await game.snapshot()).clues).toEqual(before.clues);
  await page.reload(); await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
  await send('对，我问的是为什么去请医生。'); await expect(log).toContainText('牧师叫我去请海多克医生，所以我出门了。');
  await send('协议焦点：已解答'); await expect(log).toContainText('这件事刚才说清楚了。');
  expect((await game.snapshot()).clues).toEqual(before.clues);
  await page.context().storageState({ path: '.build-cache/focus-browser-state.json' });
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

test('selected material ids and complete text pass from checkboxes to this turn of model input', async ({ page }) => {
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'End-to-end model-input protocol fixture.');
  const game = await start(page); await game.read('route_redding_account');
  await game.change('executeoperation', { operation: 'open_anne_meeting' });
  await game.read('route_anne_time'); await game.read('route_anne_gun'); await game.read('route_police_medical');
  await game.change('requestexternalcheck', { check: 'medical_timing' }); await game.change('advancetime', { minutes: 15 });
  const state = await game.snapshot();
  const anne = ['clue_anne_time', 'clue_anne_gun'];
  const medical = ['clue_medical_time', 'clue_medical_cause', 'clue_medical_visit_record'];
  const clue = (id: string) => state.clues.find((c: { id: string }) => c.id === id);
  const requests: { evidence: string[] }[] = [];
  page.on('request', request => { if (request.url().endsWith('/api/askwitness')) requests.push(request.postDataJSON()); });
  await page.goto('/interviews'); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  const panel = page.getByRole('region', { name: '自由问询', exact: true });
  for (const id of anne) await panel.getByRole('checkbox', { name: clue(id).title, exact: true }).check();
  await page.getByLabel('你的问题', { exact: true }).fill('协议材料：核对本轮陈述正文');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  const log = page.getByRole('log', { name: '自由问询记录', exact: true });
  for (const id of anne) await expect(log).toContainText(clue(id).text);
  expect(requests[0]!.evidence.sort()).toEqual([...anne].sort());
  for (const id of anne) await panel.getByRole('checkbox', { name: clue(id).title, exact: true }).uncheck();
  for (const id of medical) await panel.getByRole('checkbox', { name: clue(id).title, exact: true }).check();
  await page.getByLabel('你的问题', { exact: true }).fill('协议材料：核对本轮医生正文');
  await page.getByRole('button', { name: '发送问题', exact: true }).click();
  const last = log.getByRole('article').last();
  for (const id of medical) await expect(last).toContainText(clue(id).text);
  await expect(last).not.toContainText(clue('clue_anne_gun').text);
  expect(requests[1]!.evidence.sort()).toEqual([...medical].sort());
  const after = await game.snapshot(); expect(after.interviews.at(-1).shownClues.sort()).toEqual([...medical].sort());
  await page.reload(); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await expect(log).toContainText(clue('clue_medical_visit_record').text);
  await page.context().storageState({ path: '.build-cache/material-reading-browser-state.json' });
});
