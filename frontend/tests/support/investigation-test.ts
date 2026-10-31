import { test as base, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';

// Existing investigation regressions begin with an explicitly created investigation.
// Website-entry tests use Playwright directly, and must exercise the empty initial state.
export const test = base.extend({
  page: async ({ page }, use) => {
    const key = 'detective-scenic:server-session:v6';
    const storage = await page.context().storageState();
    if (!storage.origins.some(origin => origin.localStorage.some(item => item.name === key || item.name === 'detective-scenic:server-session:v5'))) {
      const headers = { 'X-Detective-Client': 'web' };
      expect((await page.request.post('/api/openlocalidentity', { data: {}, headers })).status()).toBe(200);
      const catalog = await page.request.post('/api/getcasecatalog', { data: {}, headers });
      expect(catalog.status()).toBe(200);
      const entry = (await catalog.json()).find((item: { caseId: string }) => item.caseId === 'vicarage_study');
      expect(entry).toBeDefined();
      const sessionId = randomUUID();
      const response = await page.request.post('/api/openinvestigation', { data: { sessionId, caseId: entry.caseId, revision: entry.revision }, headers });
      expect(response.status()).toBe(200);
      await page.addInitScript(({ locatorKey, value }) => {
        if (!localStorage.getItem(locatorKey)) localStorage.setItem(locatorKey, value);
      }, { locatorKey: key, value: JSON.stringify({ version: 1, sessionId, creationPending: false, pending: null }) });
    }
    await use(page);
  },
});
export { expect };
export type { Page } from '@playwright/test';
