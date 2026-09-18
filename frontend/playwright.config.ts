import { defineConfig, devices } from '@playwright/test';

/**
 * #239/#242 — the browser half of the screen's tests.
 *
 * Runs against the PRODUCTION build served by `vite preview`, because that is what a player loads: the base path, the
 * lazy registry and the code-split chunks are all build-time facts a dev server does not have. With no
 * VITE_DRAUGR_API_URL the build runs in prototype mode, so these need no backend and no database — they test the
 * screen's framing, not the world.
 *
 * Named `*.e2e.ts` rather than `*.spec.ts` so vitest's default pattern never picks them up: `npm test` stays a
 * sub-second, browser-free gate, and this is `npm run test:e2e`.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: /.*\.e2e\.ts$/,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4173/projectdraugr/',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npx vite preview --port 4173 --strictPort',
    url: 'http://localhost:4173/projectdraugr/',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
