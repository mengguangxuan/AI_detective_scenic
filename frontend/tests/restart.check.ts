import { readFileSync, existsSync } from 'node:fs';
import { test, expect } from '@playwright/test';
if (existsSync('.build-cache/open-meetings-state.json')) test.describe('revision25 early contact recovery', () => {
  test.use({ storageState: '.build-cache/open-meetings-state.json' });
  test('freely arranged people and actual early Archer testimony restore exactly after restart', async ({ page }) => {
    await page.goto('/interviews');
    await page.getByRole('button', { name: '与阿彻太太交谈', exact: true }).click();
    await expect(page.getByRole('heading', { name: '向阿彻太太提问', exact: true })).toBeVisible();
    const sessionId = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!).sessionId);
    const response = await page.request.post('/api/getinvestigationsnapshot', { headers: { 'X-Detective-Client': 'web' }, data: { sessionId } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state).toEqual(JSON.parse(readFileSync('.build-cache/open-meetings-snapshot.json', 'utf8')));
    expect(state.caseRevision.value).toBe(25);
    for (const id of ['anne','valet','stone','hartnell','archer']) expect(state.witnesses.some((w: { id: string }) => w.id === id)).toBe(true);
  });
});
if (existsSync('.build-cache/semantic-browser-state.json')) test.describe('semantic registration recovery', () => {
  test.use({ storageState: '.build-cache/semantic-browser-state.json' });
  test('actual speech and per-claim recognition restore exactly after backend restart', async ({ page }) => {
    await page.goto('/interviews');
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    await expect(page.getByLabel('本轮调查记录', { exact: true })).toContainText('牧师谈座钟');
    const sessionId = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!).sessionId);
    const response = await page.request.post('/api/getinvestigationsnapshot', { headers: { 'X-Detective-Client': 'web' }, data: { sessionId } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/semantic-browser-snapshot.json', 'utf8')));
  });
});
if (existsSync('.build-cache/semantic-completion/completion-live-solved-state.json')) test.describe('revision26 semantic ending recovery', () => {
  test.use({ storageState: '.build-cache/semantic-completion/completion-live-solved-state.json' });
  test('semantic testimony and the real established ending remain in the original frozen review', async ({ page }) => {
    await page.goto('/conclusion');
    await expect(page.getByRole('region', { name: '推理评审记录', exact: true })).toBeVisible();
    const sessionId = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!).sessionId);
    const response = await page.request.post('/api/getinvestigationsnapshot', { headers: { 'X-Detective-Client': 'web' }, data: { sessionId } });
    expect(response.status()).toBe(200); const state = await response.json();
    expect(state).toEqual(JSON.parse(readFileSync('.build-cache/semantic-completion/completion-live-solved-snapshot.json', 'utf8')));
    expect(state.caseRevision.value).toBe(26); expect(state.reasoning.ending.mode).toBe('Solved');
    expect(state.reasoning.ending.sections).toHaveLength(6);
  });
});
test.describe('case hub recovery', () => {
  test.use({ storageState: '.build-cache/case-hub-state.json' });
  test('the home page lists both saved investigations and restores the original without creating another', async ({ page }) => {
    let creations = 0;
    page.on('request', request => { if (/\/api\/(openstudy|openinvestigation)$/.test(request.url())) creations++; });
    await page.goto('/');
    await expect(page.getByRole('button', { name: '继续调查', exact: true })).toHaveCount(2);
    await page.getByRole('listitem').filter({ hasText: '1条记录' }).getByRole('button', { name: '继续调查', exact: true }).click();
    await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const snapshot = await response.json();
    expect(snapshot.sequence.value).toBe(1);
    expect(snapshot.clues.map((clue: { id: string }) => clue.id)).toEqual(['clue_clock']);
    expect(creations).toBe(0);
  });
});
test.describe('presentation history recovery', () => {
  test.use({ storageState: '.build-cache/presentation-state.json' });
  test('a presentation restores under its actual witness without adding a clue after backend restart', async ({ page }) => {
    await page.goto('/interviews');
    await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
    await expect(page.getByRole('region', { name: '已出示的材料', exact: true })).toContainText('纸张摘录');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    const snapshot = await response.json();
    expect(snapshot.clues).toHaveLength(6);
    expect(snapshot.points.find((p: { id: string }) => p.id === 'point_mary').presentations).toHaveLength(1);
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    await expect(page.getByRole('region', { name: '已出示的材料', exact: true })).toHaveCount(0);
  });
});
test('same identity restores the completed stage after a backend process restart', async ({ page }) => {
  await page.goto('/clues');
  await expect(page.getByRole('heading', { name: '调查目标' })).toBeVisible();
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  const snapshot = await response.json();
  expect(snapshot.sequence.value).toBe(6);
  expect(snapshot.journal).toHaveLength(6);
  expect(snapshot.clues).toHaveLength(6);
  expect(snapshot.stage.hasTimeComparison).toBe(false);
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
    expect(snapshot.clues).toHaveLength(6);
    expect(snapshot.stage.hasTimeComparison).toBe(false);
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
    expect(state.caseRevision.value).toBe(21);
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


test.describe('woodland recovery', () => {
  test.use({ storageState: '.build-cache/woodland-state.json' });
  test('sample, tentative opinion, original hearings and police presentation restore exactly after restart', async ({ page }) => {
    await page.goto('/police');
    await expect(page.getByRole('article', { name: '树林样本初步辨认', exact: true })).toContainText('已完成');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/woodland-snapshot.json', 'utf8')));
  });
});

test.describe('conclusion recovery', () => {
  test.use({ storageState: '.build-cache/conclusion-state.json' });
  test('the frozen submission, scores, original citations and review restore exactly after a real restart', async ({ page }) => {
    await page.goto('/conclusion');
    await expect(page.getByRole('region', { name: '已保存的推理与复盘', exact: true })).toContainText('本次推理已保存');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/conclusion-snapshot.json', 'utf8')));
  });
});

for (const [name, outcome] of [['reasoning-open', null], ['reasoning-solved', 'Solved'], ['reasoning-revealed', 'Revealed']] as const) {
  test.describe(`${name} recovery`, () => {
    test.use({ storageState: `.build-cache/${name}-state.json` });
    test('assessment history and separate ending restore exactly after an actual process restart', async ({ page }) => {
      await page.goto('/conclusion');
      await expect(page.getByRole('region', { name: '推理评审记录', exact: true })).toBeVisible();
      const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
      const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
      expect(response.status()).toBe(200);
      const state = await response.json();
      expect(state).toEqual(JSON.parse(readFileSync(`.build-cache/${name}-snapshot.json`, 'utf8')));
      expect(state.reasoning.ending?.mode ?? null).toBe(outcome);
    });
  });
}

test.describe('planter discovery recovery', () => {
  test.use({ storageState: '.build-cache/planter-state.json' });
  test('closed pot and discovered clue restore together after an actual backend restart', async ({ page }) => {
    await page.goto('/scene');
    await expect(page.getByRole('heading', { name: '调查目标', exact: true })).toBeVisible();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/planter-snapshot.json', 'utf8')));
  });
});

test.describe('pre-crime recollection recovery', () => {
  test.use({ storageState: '.build-cache/before-crime-state.json' });
  test('actual recollections and sequence restore after a backend restart', async ({ page }) => {
    await page.goto('/interviews');
    await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/before-crime-snapshot.json', 'utf8')));
  });
});

for (const [name, ending] of [['completion-live-solved', 'Solved'], ['completion-live-paraphrase', null]] as const) {
  const statePath = `.build-cache/${name}-state.json`;
  if (existsSync(statePath)) test.describe(`${name} real-model recovery`, () => {
    test.use({ storageState: statePath });
    test('real-model assessment and ending survive process restart without another completion', async ({ page }) => {
      await page.goto('/conclusion');
      await expect(page.getByRole('region', { name: '推理评审记录', exact: true })).toBeVisible();
      const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
      const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
      expect(response.status()).toBe(200);
      const state = await response.json();
      expect(state).toEqual(JSON.parse(readFileSync(`.build-cache/${name}-snapshot.json`, 'utf8')));
      expect(state.reasoning.attempts.at(-1).status).toBe('Established');
      expect(state.reasoning.ending?.mode ?? null).toBe(ending);
    });
  });
}

test.describe('frozen review comparison recovery', () => {
  test.use({ storageState: '.build-cache/review-comparison-state.json' });
  test('later evidence does not rewrite the saved review after a real process restart', async ({ page }) => {
    await page.goto('/conclusion');
    await expect(page.getByRole('region', { name: '结案时的材料对照', exact: true })).toBeVisible();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/review-comparison-snapshot.json', 'utf8')));
  });
});

test.describe('case-wide ending status recovery', () => {
  test.use({ storageState: '.build-cache/progress-solved-state.json' });
  test('continued investigation keeps its saved ending and progress banner after a real restart', async ({ page }) => {
    await page.goto('/case');
    await expect(page.getByLabel('调查状态', { exact: true })).toContainText('推理已结案');
    await expect(page.getByRole('heading', { name: '本次推理已结案', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: '当前阶段 · 书房初查', exact: true })).toHaveCount(0);
    await page.getByRole('link', { name: '查看结案复盘', exact: true }).click();
    await expect(page.getByRole('region', { name: '本案收尾与复盘', exact: true })).toBeVisible();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual(JSON.parse(readFileSync('.build-cache/progress-solved-snapshot.json', 'utf8')));
  });
});

test.describe('continuous statement recovery', () => {
  test.use({ storageState: '.build-cache/continuous-browser-state.json' });
  test('new investigation restores actual versions without a phase operation after restart', async ({ page }) => {
    await page.goto('/interviews');
    await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200); const snapshot = await response.json();
    expect(snapshot.caseRevision.value).toBe(22); expect(snapshot.followUps).toEqual([]);
    expect(snapshot.clues.some((c: { id: string }) => c.id === 'clue_redding_account')).toBe(true);
    const revised = snapshot.clues.find((c: { id: string }) => c.id === 'clue_redding_revised');
    expect(revised).toBeDefined();
    await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText(revised.text);
    await expect(page.getByRole('button', { name: '开始补充问询', exact: true })).toHaveCount(0);
  });
});

test.describe('character dialogue recovery', () => {
  test.use({ storageState: '.build-cache/character-dialogue-browser-state.json' });
  test('counterquestions and player replies survive restart without creating revised statements', async ({ page }) => {
    await page.goto('/interviews'); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
    const log = page.getByRole('log', { name: '自由问询记录', exact: true });
    await expect(log).toContainText('你们为什么怀疑安妮？');
    await expect(log).toContainText('只是你的判断？那我先前说的没有什么要改的。');
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200); const state = await response.json();
    expect(state.caseRevision.value).toBe(23);
    expect(state.clues.some((c: { id: string }) => c.id.endsWith('_revised'))).toBe(false);
  });
});
test.describe('character fallback recovery', () => {
  test.use({ storageState: '.build-cache/character-fallback-browser-state.json' });
  test('revision23 statements and Archer contact restore after restart', async ({ page }) => {
    await page.goto('/interviews'); await page.getByRole('button', { name: '与阿彻太太交谈', exact: true }).click();
    await expect(page.getByRole('heading', { name: '向阿彻太太提问', exact: true })).toBeVisible();
    const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
    const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
    expect(response.status()).toBe(200); const state = await response.json();
    expect(state.caseRevision.value).toBe(23); expect(state.followUps).toEqual([]);
    for (const id of ['clue_redding_gun', 'clue_redding_gun_revised', 'clue_police_archer_review']) expect(state.clues.some((c: { id: string }) => c.id === id)).toBe(true);
  });
});

test.describe('selected material reading recovery', () => {
  test.use({ storageState: '.build-cache/material-reading-browser-state.json' });
  test('actual selected statements and medical text restore with the original question after restart', async ({ page }) => {
    await page.goto('/interviews'); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
    const log = page.getByRole('log', { name: '自由问询记录', exact: true });
    await expect(log).toContainText('协议材料：核对本轮医生正文');
    await expect(log).toContainText('半小时左右');
    await expect(log).toContainText('约19:00是到场');
  });
});

test.describe('short-answer continuity recovery', () => {
  test.use({ storageState: '.build-cache/continuity-browser-state.json' });
  test('saved short-answer exchange and witness separation survive actual restart', async ({ page }) => {
    await page.goto('/interviews'); await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
    const log = page.getByRole('log', { name: '自由问询记录', exact: true });
    await expect(log).toContainText('你是说抽屉里的那支枪吗？');
    await expect(log).toContainText('明白，你指的是抽屉里的那支枪。');
    await expect(log).not.toContainText('这是我们这次的第一句交谈。');
  });
});

test.describe('clarification focus recovery', () => {
  test.use({ storageState: '.build-cache/focus-browser-state.json' });
  test('the clarification and its actual answer survive backend restart without granting clues', async ({ page }) => {
    await page.goto('/interviews'); await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
    const log = page.getByRole('log', { name: '自由问询记录', exact: true });
    await expect(log).toContainText('你是问他为什么让我去请医生吗？');
    await expect(log).toContainText('对，我问的是为什么去请医生。');
    await expect(log).toContainText('这件事刚才说清楚了。');
  });
});

test.describe('woodland exploration recovery', () => {
  test.use({ storageState: '.build-cache/woodland-exploration-browser-state.json' });
  test('the actual woodland stop, uncovered ground and acquired sample restore after process restart', async ({ page }) => {
    await page.goto('/scene'); await expect(page.getByLabel('树林三维场景', { exact: true })).toBeVisible();
    const sessionId = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!).sessionId);
    const response = await page.request.post('/api/getinvestigationsnapshot', { headers: { 'X-Detective-Client': 'web' }, data: { sessionId } });
    expect(response.status()).toBe(200);
    const state = await response.json();
    expect(state).toEqual(JSON.parse(readFileSync('.build-cache/woodland-exploration-snapshot.json', 'utf8')));
    expect(state.scene.playerPosition).toEqual({ x: 2.8, y: 0, z: -4.5 });
    expect(state.clues.filter((c: { id: string }) => c.id === 'clue_woodland_sample')).toHaveLength(1);
  });
});

if (existsSync('.build-cache/woodland-completion/completion-live-solved-state.json')) test.describe('revision24 woodland real-model ending recovery', () => {
  test.use({ storageState: '.build-cache/woodland-completion/completion-live-solved-state.json' });
  test('manual discovery and the real assessment remain in their original frozen review', async ({ page }) => {
    await page.goto('/conclusion'); await expect(page.getByRole('region', { name: '推理评审记录', exact: true })).toBeVisible();
    const sessionId = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!).sessionId);
    const response = await page.request.post('/api/getinvestigationsnapshot', { headers: { 'X-Detective-Client': 'web' }, data: { sessionId } });
    expect(response.status()).toBe(200); const state = await response.json();
    expect(state).toEqual(JSON.parse(readFileSync('.build-cache/woodland-completion/completion-live-solved-snapshot.json', 'utf8')));
    expect(state.reasoning.ending.mode).toBe('Solved'); expect(state.reasoning.ending.sections).toHaveLength(6);
    expect(state.clues.find((c: { id: string }) => c.id === 'clue_woodland_sample').text).toContain('现场采集记录');
  });
});
