import { test, expect, type Page } from './support/investigation-test';
test.skip(process.env.DETECTIVE_TEST_MODEL !== '1', 'Uses the local model fixture, never a paid model.');

async function send(page: Page, question: string) {
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  await form.getByLabel('你的问题', { exact: true }).fill(question);
  await form.getByRole('button', { name: '发送问题', exact: true }).click();
  await expect(form.getByLabel('你的问题', { exact: true })).toHaveValue('');
  return page.getByRole('log', { name: '自由问询记录', exact: true });
}
async function snapshot(page: Page) {
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('detective-scenic:server-session:v6')!));
  const response = await page.request.post('/api/getinvestigationsnapshot', { data: { sessionId: saved.sessionId }, headers: { 'X-Detective-Client': 'web' } });
  expect(response.status()).toBe(200);
  return response.json();
}

test('personal context replies remain conversational without requiring a clue token', async ({ page }) => {
  await page.goto('/interviews');
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  await expect(await send(page, '斯莱克是谁？')).toContainText('调查本案的警探');
  await expect(await send(page, '病人是谁？')).toContainText('阿博特先生');
  await expect(await send(page, '你什么时候离开的寓所？')).toContainText('五点半接到电话');
  const saved = await snapshot(page);
  expect(saved.interviews).toHaveLength(3);
  expect(saved.clues).toHaveLength(0);
  expect(saved.interviews.every((turn: { mode: string }) => turn.mode === 'answered')).toBe(true);
  await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
  const history = await send(page, '你听到过异响吗？');
  await expect(history).toContainText('以为在树林');
  await expect(history).not.toContainText('阿博特');
  await page.reload();
  await page.getByRole('button', { name: '与克莱门特牧师交谈', exact: true }).click();
  await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('阿博特先生');
  await page.screenshot({ path: 'test-results/dialogue-personal-context.png', fullPage: true });
});

test('police answer through the model and remember evidence shown with a conversational question', async ({ page }) => {
  await page.goto('/scene');
  await page.getByText('物品列表（备用入口）', { exact: true }).click();
  await page.locator('details').getByRole('button', { name: '座钟', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '座钟操作', exact: true })).toContainText('18:22');
  await page.keyboard.press('Escape');
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name: '警方交流', exact: true }).click();
  await expect(await send(page, '医生对死因和死亡时间有什么意见？')).toContainText('半小时左右');
  const form = page.getByRole('region', { name: '自由问询', exact: true });
  await form.getByRole('checkbox', { name: '座钟读数', exact: true }).check();
  await expect(await send(page, '这份材料你怎么看？')).toContainText('18:22');
  const saved = await snapshot(page);
  expect(saved.interviews).toHaveLength(2);
  expect(saved.interviews.every((turn: { witness: string }) => turn.witness === 'melchett')).toBe(true);
  expect(saved.police.sharedClues).toEqual(['clue_clock']);
  expect(saved.police.exchanges).toHaveLength(1);
  expect(saved.clues.some((clue: { id: string }) => clue.id === 'clue_medical_time')).toBe(true);
  await page.reload();
  await expect(page.getByRole('log', { name: '自由问询记录', exact: true })).toContainText('这份材料你怎么看');
  expect(await snapshot(page)).toEqual(saved);
  await page.screenshot({ path: 'test-results/dialogue-police-model.png', fullPage: true });
});
