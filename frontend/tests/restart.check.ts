import { readFileSync } from 'node:fs';
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

test.describe('map and timed check recovery', () => {
  test.use({ storageState: '.build-cache/map-clock-state.json' });
  test('completed external checks and exact story time restore after backend restart', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('region', { name: '案件时钟', exact: true })).toContainText('09:29');
    await expect(page.getByRole('article', { name: '手枪与弹头核验', exact: true })).toContainText('已完成');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state.clock.elapsedSeconds).toBe(1740);
    expect(state.externalChecks.filter((check: { progress: unknown }) => check.progress !== null).every((check: { progress: { completedAt: string | null } | null }) => check.progress?.completedAt !== null)).toBe(true);
    expect(state.clues).toHaveLength(2);
    expect(state.areaMap.locations).toHaveLength(15);
  });
});

test.describe('supplementary statement recovery', () => {
  test.use({ storageState: '.build-cache/followup-state.json' });
  test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Requires saved follow-up state.');
  test('both archived versions and completed follow-ups restore after a real backend restart', async ({ page }) => {
    await page.goto('/clues');
    const comparison = page.getByRole('region', { name: '供述版本对照', exact: true });
    await expect(comparison).toContainText('第 1 条记录');
    await expect(comparison).toContainText('没有碰尸体');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state.followUps.every((item: { started: boolean }) => item.started)).toBe(true);
    expect(state.statementComparisons.every((item: { rows: { initial: unknown; revised: unknown }[] }) => item.rows.every(row => row.initial && row.revised))).toBe(true);
    expect(state.clues.some((clue: { id: string }) => clue.id === 'clue_police_revised_review')).toBe(true);
  });
});


test.describe('Archer inquiry recovery', () => {
  test.use({ storageState: '.build-cache/archer-inquiry-state.json' });
  test('contact, testimony, presentations and exact progress survive an actual backend restart', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('region', { name: '已联系的核查对象', exact: true })).toContainText('阿彻太太');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state).toEqual(JSON.parse(readFileSync('.build-cache/archer-inquiry-snapshot.json', 'utf8')));
    expect(state.caseRevision.value).toBe(14);
    await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '人物问询', exact: true }).click();
    await page.getByRole('button', { name: '与阿彻太太交谈', exact: true }).click();
    await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('十二点三刻');
  });
});


test.describe('paper check recovery', () => {
  test.use({ storageState: '.build-cache/paper-checks-state.json' });
  test('both paper results, original excerpt and police registration restore exactly after restart', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('article', { name: '纸张笔迹比对', exact: true })).toContainText('已完成');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/paper-checks-snapshot.json', 'utf8')));
  });
});


test.describe('sightline recovery', () => {
  test.use({ storageState: '.build-cache/sightline-state.json' });
  test('the actual reenactment, witness responses and original accounts restore exactly after a process restart', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('article', { name: '窗边视线复验', exact: true })).toContainText('已完成');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/sightline-snapshot.json', 'utf8')));
  });
});


test.describe('call checks recovery', () => {
  test.use({ storageState: '.build-cache/call-checks-state.json' });
  test('traced origin, site inspection, original account and witness responses restore exactly after restart', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('article', { name: '来电接出地点现场核查', exact: true })).toContainText('已完成');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/call-checks-snapshot.json', 'utf8')));
  });
});
