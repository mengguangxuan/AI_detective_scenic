import { test, expect } from '@playwright/test';

test('preview investigation and browser restore', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'AI 侦探剧场' })).toBeVisible();
  await expect(page.getByRole('button', { name: '抽屉中的信', exact: true })).toHaveCount(0);
  await expect(page.getByText('信上写着：今晚九点见。', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '书桌抽屉', exact: true }).click();
  await page.getByRole('button', { name: '打开抽屉', exact: true }).click();
  await page.getByRole('button', { name: '抽屉中的信', exact: true }).click();
  await page.getByRole('button', { name: '阅读信件', exact: true }).click();
  await expect(page.getByText('信上写着：今晚九点见。', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('信上写着：今晚九点见。', { exact: true })).toBeVisible();
  await expect(page.locator('canvas')).toBeVisible();
  await expect(page.getByRole('button', { name: '在场景中选择信件' })).toBeVisible();
  await page.screenshot({ path: 'test-results/investigation-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/investigation-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
});

test('corrupt local state is reported and reset is explicit', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('detective-scenic:preview:v1', '{invalid'));
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('无法读取');
  page.on('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: '重新开始预览' }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.getByRole('status')).toContainText('已重新开始');
});
