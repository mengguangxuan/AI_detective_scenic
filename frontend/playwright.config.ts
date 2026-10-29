import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests',
  // Local software WebGL shares the CPU with Scala; parallel canvases starve HTTP timers.
  workers: 1,
  use: { baseURL: 'http://127.0.0.1:5175', screenshot: 'only-on-failure',
    launchOptions: { args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] } },
  webServer: { command: 'npm run build && npm run preview -- --port 5175 --strictPort', url: 'http://127.0.0.1:5175', reuseExistingServer: false },
});
