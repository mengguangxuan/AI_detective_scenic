import { test, expect, type Page } from '@playwright/test';
import { PerspectiveCamera, Vector3 } from 'three';
const key = 'detective-scenic:investigation-session';
const headers = { 'X-Detective-Client': 'web' };
async function snapshot(page: Page) {
  const sessionId = await page.evaluate(key => JSON.parse(localStorage.getItem(key)!).sessionId, key);
  const response = await page.request.post('/api/getinvestigationsnapshot', { headers, data: { sessionId } });
  expect(response.status()).toBe(200); return response.json();
}
async function start(page: Page) {
  await page.goto('/cases/speckled_band');
  await expect(page.getByRole('region', { name: '案件介绍', exact: true })).toContainText('夜间守候尚未开放');
  await page.getByRole('button', { name: '开始新调查', exact: true }).click();
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '现场探索', exact: true }).click();
}
async function item(page: Page, name: string, action: string) {
  const before = await snapshot(page);
  const list = page.locator('details').filter({ has: page.getByText('物品列表（备用入口）', { exact: true }) });
  if (!(await list.evaluate(element => (element as HTMLDetailsElement).open))) await list.locator('summary').click();
  await list.getByRole('button', { name, exact: true }).click();
  await page.getByRole('dialog', { name: `${name}操作`, exact: true }).getByRole('button', { name: action, exact: true }).click();
  await expect.poll(async () => (await snapshot(page)).sequence.value).toBe(before.sequence.value + 1);
  await page.keyboard.press('Escape');
}
async function travel(page: Page, label: string, place: string) {
  const before = await snapshot(page);
  await page.getByRole('region', { name: '可行走的路线', exact: true }).getByRole('button', { name: label, exact: true }).click();
  await expect.poll(async () => (await snapshot(page)).scene.placeId).toBe(place);
  const after = await snapshot(page);
  expect(after.clock.elapsedSeconds - before.clock.elapsedSeconds).toBe(300);
}
test('Speckled Band daytime travels through independent authored rooms, earns bounded evidence and saves notes without revealing a solution', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  const mutations: Array<Promise<{ request: unknown; receipt: unknown }>> = [];
  page.on('response', response => {
    if (response.url().endsWith('/api/investigate') && response.request().postDataJSON().route === 'route_bed') {
      const request = response.request().postDataJSON();
      mutations.push(response.json().then(receipt => ({ request, receipt })));
    }
  });
  await start(page);
  await expect(page.getByLabel('庄园草坪与窗外三维场景', { exact: true })).toBeVisible();
  await item(page, '海伦·斯托纳', '询问海伦的经历');
  await item(page, '已有文件与调查笔记', '查阅遗嘱核查摘要');
  await travel(page, '前往卧室走廊', 'corridor');
  await travel(page, '前往中间卧室', 'middle_room');
  await expect(page.getByLabel('中间卧室三维场景', { exact: true }).locator('canvas')).toBeVisible();
  const frame = page.getByLabel('中间卧室三维场景', { exact: true });
  const bounds = (await frame.boundingBox())!;
  const state = await snapshot(page), room = state.scene.render.room;
  const camera = new PerspectiveCamera(43, bounds.width / bounds.height, 0.1, 80);
  camera.position.set(room.cameraPosition.x, room.cameraPosition.y, room.cameraPosition.z);
  camera.lookAt(room.cameraTarget.x, room.cameraTarget.y, room.cameraTarget.z); camera.updateMatrixWorld();
  const bed = state.scene.render.instances.find((i: { node: string }) => i.node === 'sb_node_bed');
  const projected = new Vector3(bed.position.x, bed.position.y + 0.5, bed.position.z).project(camera);
  await frame.click({ position: { x: (projected.x + 1) * bounds.width / 2, y: (1 - projected.y) * bounds.height / 2 } });
  await expect(page.getByRole('dialog', { name: '固定床操作', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  for (const [name, action] of [['床边拉绳', '检查拉绳连接'], ['墙面通气孔', '检查通气孔方向'], ['固定床', '检查床与地板'], ['窗户与百叶窗', '检查窗户与百叶窗']]) await item(page, name!, action!);
  await page.screenshot({ path: '.build-cache/speckled-middle-room.png', fullPage: true });
  await travel(page, '前往卧室走廊', 'corridor');
  await travel(page, '前往罗伊洛特卧室', 'roylott_room');
  await item(page, '铁柜外部', '检查铁柜周围');
  await item(page, '套圈短鞭', '检查短鞭形状');
  const inspected = await snapshot(page);
  expect(inspected.clues.some((c: { id: string }) => ['clue_helen_safe_memory', 'clue_final_scene', 'clue_signal'].includes(c.id))).toBe(false);
  await page.screenshot({ path: '.build-cache/speckled-roylott-room.png', fullPage: true });
  await travel(page, '前往卧室走廊', 'corridor');
  await travel(page, '前往庄园草坪与窗外', 'grounds');
  await item(page, '已有文件与调查笔记', '整理白天调查笔记');
  const complete = await snapshot(page);
  expect(complete.stage.completed).toBe(true); expect(complete.reasoning).toBeNull(); expect(complete.conclusion).toBeNull();
  expect(complete.clues).toHaveLength(9);
  expect(JSON.stringify(complete)).not.toContain('f_snake_route_truth');
  await page.reload(); expect(await snapshot(page)).toEqual(complete);
  await expect(page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '警方交流', exact: true })).toHaveCount(0);
  await page.context().storageState({ path: '.build-cache/speckled-daytime-state.json' });
  const { writeFileSync } = await import('node:fs');
  writeFileSync('.build-cache/speckled-daytime-snapshot.json', JSON.stringify(complete));
  const originals = await Promise.all(mutations); expect(originals).toHaveLength(1);
  writeFileSync('.build-cache/speckled-daytime-receipt.json', JSON.stringify(originals[0]));
  expect(errors).toEqual([]);
});

test('Speckled Band remains playable without WebGL and waiting cannot cross the unopened night stage', async ({ page }) => {
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (kind: string, ...args: unknown[]) {
      if (kind === 'webgl' || kind === 'webgl2') return null;
      return original.apply(this, [kind, ...args] as never);
    } as typeof original;
  });
  await start(page);
  await item(page, '已有文件与调查笔记', '查阅遗嘱核查摘要');
  let current = await snapshot(page);
  while (current.clock.elapsedSeconds < 12000) {
    const minutes = Math.min(30, (12000 - current.clock.elapsedSeconds) / 60);
    const response = await page.request.post('/api/advancetime', { headers, data: { sessionId: current.sessionId, requestId: crypto.randomUUID(), expectedSequence: current.sequence, minutes } });
    expect(response.status()).toBe(200); current = await snapshot(page);
  }
  await page.reload();
  await expect(page.getByRole('button', { name: '整理并等待5分钟', exact: true })).toBeDisabled();
  const denied = await page.request.post('/api/advancetime', { headers, data: { sessionId: current.sessionId, requestId: crypto.randomUUID(), expectedSequence: current.sequence, minutes: 1 } });
  expect(denied.status()).toBe(409);
  expect(await snapshot(page)).toEqual(current);
});

test('two actual cases under one browser identity keep independent evidence when switching investigations', async ({ page }) => {
  await page.goto('/cases/vicarage_study');
  await page.getByRole('button', { name: '开始新调查', exact: true }).click();
  await expect(page.getByRole('heading', { name: '案件简介', exact: true })).toBeVisible();
  const first = await snapshot(page);
  const clockPoint = first.points.find((p: { routes: Array<{ id: string }> }) => p.routes.some(r => r.id === 'route_clock'));
  const read = await page.request.post('/api/investigate', { headers, data: { sessionId: first.sessionId, requestId: crypto.randomUUID(), expectedSequence: first.sequence, point: clockPoint.id, route: 'route_clock' } });
  expect(read.status()).toBe(200);
  const firstSaved = await snapshot(page);
  await start(page);
  const independent = await snapshot(page);
  expect(independent.caseId).toBe('speckled_band'); expect(independent.clues).toEqual([]);
  await item(page, '已有文件与调查笔记', '查阅遗嘱核查摘要');
  const secondSaved = await snapshot(page);
  expect(secondSaved.clues.map((c: { id: string }) => c.id)).toEqual(['clue_will']);
  await page.goto('/cases');
  await page.locator('li').filter({ has: page.getByRole('heading', { name: firstSaved.title, exact: true }) }).getByRole('button', { name: '继续调查', exact: true }).click();
  await expect.poll(async () => (await snapshot(page)).sessionId).toBe(firstSaved.sessionId);
  expect(await snapshot(page)).toEqual(firstSaved);
  await page.goto('/cases');
  await page.locator('li').filter({ has: page.getByRole('heading', { name: secondSaved.title, exact: true }) }).getByRole('button', { name: '继续调查', exact: true }).click();
  await expect.poll(async () => (await snapshot(page)).sessionId).toBe(secondSaved.sessionId);
  expect(await snapshot(page)).toEqual(secondSaved);
});
