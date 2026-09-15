import { defineConfig, devices } from '@playwright/test';

// End-to-end tests run against the Vite dev server with the REST API mocked via page.route,
// so no running Polarion is required.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    port: 5173,
    reuseExistingServer: !process.env.CI,
  },
});
