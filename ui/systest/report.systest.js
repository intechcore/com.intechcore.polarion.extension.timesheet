import { expect, test } from '@playwright/test';
import {
  BASE_URL,
  PERIOD,
  PROJECT,
  TOKEN,
  openReport,
  removeSeededRecords,
  seed,
  signIn,
  twoUsers,
} from './polarion.mjs';

/** Two users of the instance, in the order the report shows them. */
let users;

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ browser }) => {
  test.skip(!TOKEN, 'POLARION_TOKEN is needed to prepare the work records');
  const page = await browser.newPage();
  await signIn(page);
  users = await twoUsers(page);
  await page.close();
  await seed(users);
});

test.afterAll(async () => {
  if (users) await removeSeededRecords();
});

test('shows the seeded week of both users', async ({ page }) => {
  await signIn(page);
  await openReport(page, users);

  const text = await page.locator('body').innerText();
  expect(text).toContain('systest timesheet one');
  expect(text).toContain('systest timesheet two');
  // 8 + 4 + 3.5 hours for the first user, 6 + 2 + 4 for the second.
  expect(text).toContain('total: 15.5 h');
  expect(text).toContain('total: 12 h');
});

test('the report looks the way it is published', async ({ page }) => {
  test.skip(!process.env.PIXEL_REFERENCES, 'The reference is locked to the pinned Playwright image');

  await signIn(page);
  await openReport(page, users);

  await expect(page).toHaveScreenshot('report.png', { fullPage: true, animations: 'disabled' });
});

test('serves the about page of the extension', async ({ page }) => {
  await signIn(page);
  await page.goto(`${BASE_URL}/polarion/timesheet-app/ui/app/index.html?feature=about`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(4000);

  const text = await page.locator('body').innerText();
  expect(text).toContain('Timesheet');
  expect(text).not.toContain('shields.io');
  expect(PROJECT).toBeTruthy();
  expect(PERIOD.start < PERIOD.end).toBeTruthy();
});
