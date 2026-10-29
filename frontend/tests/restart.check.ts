import { test, expect } from '@playwright/test';
test('same identity restores the completed stage after a backend process restart', async ({ page }) => {
  await page.goto('/clues');
  await expect(page.getByRole('heading', { name: '阶段完成 · 书房初查' })).toBeVisible();
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  const snapshot = await response.json();
  expect(snapshot.sequence.value).toBe(7);
  expect(snapshot.journal).toHaveLength(7);
  expect(snapshot.clues).toHaveLength(7);
  expect(snapshot.stage.completed).toBe(true);
});

test.describe('free conversation recovery', () => {
  test.use({ storageState: '.build-cache/free-interview-browser-state.json' });
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Requires the local model fixture browser state.');
  test('saved free conversations restore after a real backend process restart without calling the model', async ({ page }) => {
    await page.goto('/interviews');
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('拨快一刻钟');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const snapshot = await response.json();
    expect(snapshot.interviews).toHaveLength(2);
    expect(snapshot.clues).toHaveLength(7);
    expect(snapshot.stage.completed).toBe(true);
    await page.screenshot({ path: 'test-results/free-interview-chat.png', fullPage: true });
  });
});

test.describe('Anne meeting recovery', () => {
  test.use({ storageState: '.build-cache/anne-meeting-state.json' });
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Requires the saved meeting fixture state.');
  test('explicit meeting and actual statement survive backend restart', async ({ page }) => {
    await page.goto('/interviews');
    await page.getByRole('button', { name: '与安妮·普罗瑟罗交谈', exact: true }).click();
    await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('是我杀了丈夫');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state.sequence.value).toBe(3);
    expect(state.clues.some((clue: { id: string }) => clue.id === 'clue_anne_statement')).toBe(true);
    expect(state.people.find((person: { id: string }) => person.id === 'anne').witness).toBe('anne');
  });
});

test.describe('independent checks recovery', () => {
  test.use({ storageState: '.build-cache/independent-checks-state.json' });
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Requires saved independent checks state.');
  test('contacted witnesses, their actual statements and police comparisons survive backend restart', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('region', { name: '已联系的核查对象', exact: true })).toContainText('哈特内尔小姐');
    await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('不能填补之前所有时段');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    for (const id of ['valet', 'stone', 'hartnell']) expect(state.witnesses.some((w: { id: string }) => w.id === id)).toBe(true);
    for (const id of ['clue_valet_gun', 'clue_stone_inn', 'clue_hartnell_visit', 'clue_police_gun_check', 'clue_police_movement_check']) expect(state.clues.some((c: { id: string }) => c.id === id)).toBe(true);
    expect(state.stage.completed).toBe(false);
  });
});
