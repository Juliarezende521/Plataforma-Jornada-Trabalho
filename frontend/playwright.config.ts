import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 120_000,
  globalTimeout: 300_000,
  reporter: [['list'], ['html', {
    open: 'never',
    outputFolder: process.env['PLAYWRIGHT_HTML_OUTPUT_DIR'] ?? 'playwright-report',
  }]],
  use: {
    baseURL: 'https://localhost:8443',
    // Ephemeral self-signed certificate in CI only; production must validate certificates.
    ignoreHTTPSErrors: true,
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { browserName: 'chromium', viewport: { width: 1280, height: 800 } } },
    { name: 'mobile-360', use: { browserName: 'chromium', viewport: { width: 360, height: 800 }, isMobile: true, hasTouch: true } },
  ],
});
