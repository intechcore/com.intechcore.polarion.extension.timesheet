import { defineConfig } from '@playwright/test';

/**
 * The system suite, against a running Polarion. It is never part of CI: it writes work records into
 * a live project and reads the report the server renders.
 *
 *   npm run systest                 the assertions on the rendered report, on this machine
 *   npm run systest:docker          the same, plus the screenshot comparison, in the pinned image
 *   npm run systest:update:docker   rewrites the committed references
 */
export default defineConfig({
  testDir: './systest',
  testMatch: /.*\.systest\.js/,
  snapshotPathTemplate: '{testDir}/expected/{arg}{ext}',
  fullyParallel: false,
  workers: 1,
  timeout: 180000,
  expect: { timeout: 30000 },
  reporter: [['list']],
  use: {
    baseURL: process.env.POLARION_URL || 'http://localhost',
    viewport: { width: 1400, height: 1000 },
    ignoreHTTPSErrors: true,
  },
});
