import { defineConfig } from '@playwright/test';
import base from './playwright.config';
export default defineConfig({ ...base, testMatch: '**/restart.check.ts',
  use: { ...base.use, storageState: '.build-cache/browser-restart-state.json' } });
