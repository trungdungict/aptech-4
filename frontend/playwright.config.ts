import { defineConfig, devices } from '@playwright/test'

/**
 * Opt-in browser E2E against the running nginx stack (WEB_PORT), not Vite.
 * Credentials: DEMO_ADMIN_* / DEMO_CUSTOMER_* from the process environment
 * (Makefile/scripts source repo `.env` — this file never reads `.env` itself).
 */
const webPort = process.env.WEB_PORT ?? '8088'
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? `http://127.0.0.1:${webPort}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
})
