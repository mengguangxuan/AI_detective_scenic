import { test, expect } from './support/investigation-test';
// Response contract checks, not an independently playable case or a substitute for Speckled Band.
test('generic map defaults and navigation use actual content without first-case places, police or named contacts', async ({ page }) => {
  await page.route('**/api/getinvestigationsnapshot', async route => {
    const response = await route.fetch();
    const snapshot = await response.json();
    await route.fulfill({ response, json: { ...snapshot, title: '独立配置展示', police: null, witnesses: [], people: [],
      points: [], interviews: [], continuousWitnesses: [], conclusionCandidates: [], conclusion: null, reasoning: null,
      observations: [], scene: { ...snapshot.scene, placeName: '庄园门口', objects: [], openedEntities: [], travel: [] },
      areaMap: { note: '作者提供的地点与道路', locations: [
        { id: 'estate_gate', name: '庄园门口', description: '公开入口', x: 200, y: 300 },
        { id: 'east_gallery', name: '东侧长廊', description: '公开长廊', x: 800, y: 300 },
      ], roads: [{ id: 'estate_walk', from: 'estate_gate', to: 'east_gallery', meters: 225, label: '连通道路' }] } } });
  });
  await page.goto('/map');
  await expect(page.getByRole('group', { name: '独立配置展示地点示意图', exact: true })).toBeVisible();
  await expect(page.getByLabel('起点', { exact: true })).toHaveValue('estate_gate');
  await expect(page.getByLabel('终点', { exact: true })).toHaveValue('east_gallery');
  await expect(page.getByRole('region', { name: '步行路线估算', exact: true })).toContainText('225 米');
  const nav = page.getByRole('navigation', { name: '调查导航' });
  for (const name of ['警方交流', '人物问询', '人物档案', '推理与复盘']) await expect(nav.getByRole('link', { name, exact: true })).toHaveCount(0);
  await expect(nav.getByRole('link', { name: '地点地图', exact: true })).toBeVisible();
  await page.screenshot({ path: '.build-cache/generic-map.png', fullPage: true });
  await page.goto('/police');
  await expect(page.getByText('当前没有可接触的警方人员。', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: /安妮|牧师/ })).toHaveCount(0);
});
