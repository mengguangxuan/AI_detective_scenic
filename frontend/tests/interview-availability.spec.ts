import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const headers = { 'X-Detective-Client': 'web' };
async function start(page: Page) {
  expect((await page.request.post('/api/openlocalidentity', { headers, data: {} })).status()).toBe(200);
  const sessionId = randomUUID();
  expect((await page.request.post('/api/openinvestigation', { headers, data: { sessionId, caseId: 'vicarage_study', revision: { value: 24 } } })).status()).toBe(200);
  await page.addInitScript(sessionId => {
    localStorage.setItem('detective-scenic:server-session:v6', JSON.stringify({ version: 1, sessionId, creationPending: false, pending: null }));
  }, sessionId);
  await page.goto('/interviews');
}
async function go(page: Page, name: string) {
  await page.getByRole('navigation', { name: '调查导航' }).getByRole('link', { name, exact: true }).click();
}
test('known witnesses stay visible offsite and their interview entries return after travelling back', async ({ page }) => {
  await start(page);
  await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
  await expect(page.getByRole('heading', { name: '向玛丽提问', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '与安妮·普罗瑟罗交谈', exact: true })).toHaveCount(0);
  await go(page, '地点地图');
  await page.getByRole('button', { name: '前往树林', exact: true }).click();
  await go(page, '人物问询');
  const offsite = page.getByRole('region', { name: '暂不可当面交谈的人物', exact: true });
  await expect(offsite).toContainText('玛丽');
  await expect(offsite).not.toContainText('安妮·普罗瑟罗');
  await expect(page.getByRole('region', { name: '自由问询', exact: true })).toHaveCount(0);
  await expect(page.getByText('选择一位人物，开始自由提问。', { exact: true })).toHaveCount(0);
  await page.reload();
  await expect(offsite).toContainText('玛丽');
  await page.getByRole('button', { name: '返回牧师寓所', exact: true }).click();
  await expect(page.getByRole('button', { name: '与玛丽交谈', exact: true })).toBeVisible();
  await expect(offsite).toHaveCount(0);
  await page.getByRole('button', { name: '与玛丽交谈', exact: true }).click();
  await expect(page.getByLabel('你的问题', { exact: true })).toBeEnabled();
  await expect(page.getByRole('link', { name: '前往警方交流，询问或安排会面', exact: true })).toBeVisible();
});

test('profiles replace unusable offsite interview links with directions and preserve arranged witnesses', async ({ page }) => {
  await start(page);
  await page.getByRole('button', { name: '与劳伦斯·雷丁交谈', exact: true }).click();
  await page.getByText('固定问答与材料笔记（备用）', { exact: true }).click();
  await page.getByRole('button', { name: '询问：你承认的经过是什么？', exact: true }).click();
  await go(page, '警方交流');
  await page.getByRole('button', { name: '安排与安妮正式会面', exact: true }).click();
  await go(page, '人物档案');
  await expect(page.getByRole('link', { name: '询问安妮·普罗瑟罗', exact: true })).toBeVisible();
  await go(page, '地点地图');
  await page.getByRole('button', { name: '前往树林', exact: true }).click();
  await go(page, '人物档案');
  await expect(page.getByRole('heading', { name: '安妮·普罗瑟罗', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '询问安妮·普罗瑟罗', exact: true })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '询问玛丽', exact: true })).toHaveCount(0);
  await expect(page.getByText('当前位置无法当面交谈。可通过上方路线返回，再继续问询。', { exact: true }).first()).toBeVisible();
  await go(page, '人物问询');
  await expect(page.getByRole('region', { name: '暂不可当面交谈的人物', exact: true })).toContainText('安妮·普罗瑟罗');
  await page.getByRole('button', { name: '返回牧师寓所', exact: true }).click();
  await page.getByRole('button', { name: '与安妮·普罗瑟罗交谈', exact: true }).click();
  await expect(page.getByRole('heading', { name: '向安妮·普罗瑟罗提问', exact: true })).toBeVisible();
});
