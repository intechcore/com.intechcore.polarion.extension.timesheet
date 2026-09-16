import { expect, test } from '@playwright/test';

const USERS = [
  { id: 'sDeveloper', name: 'Steve Developer' },
  { id: 'mTest', name: 'Melanie Test' },
  { id: 'aSeller', name: 'Ayato Seller' },
];

const SCOPES = [
  { path: '/', name: 'Repository', type: 'root', depth: 0 },
  { path: 'elibrary', name: 'E-Library', type: 'project', depth: 1 },
];

const CURRENT_USER = { id: 'sDeveloper', name: 'Steve Developer' };

const wi = (id, title) => ({ project: { id: 'elibrary', name: 'elibrary' }, id, title });
const TIMESHEET = {
  startDate: '2026-06-01',
  finishDate: '2026-06-30',
  workRecords: [
    { date: '2026-06-01', workItem: wi('EL-1', 'Title 1'), user: { id: 'sDeveloper', name: 'Steve Developer' }, hours: 8 },
    { date: '2026-06-02', workItem: wi('EL-1', 'Title 1'), user: { id: 'sDeveloper', name: 'Steve Developer' }, hours: 4 },
    { date: '2026-06-01', workItem: wi('EL-2', 'Title 2'), user: { id: 'mTest', name: 'Melanie Test' }, hours: 5 },
  ],
};

const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });

test.beforeEach(async ({ page }) => {
  // The report opens on the current month and draws a block only for a month that holds records, so
  // the clock is pinned to the month of the fixture above. Fixed time only - the app reads the date
  // once, and installing tickable timers would also stub the ones React and the pickers run on.
  await page.clock.setFixedTime(new Date('2026-06-17T09:00:00'));
  await page.route('**/rest/internal/users', (r) => r.fulfill(json(USERS)));
  await page.route('**/rest/internal/scopes', (r) => r.fulfill(json(SCOPES)));
  await page.route('**/rest/internal/current-user', (r) => r.fulfill(json(CURRENT_USER)));
  await page.route('**/rest/internal/timesheet**', (r) => r.fulfill(json(TIMESHEET)));
});

test('defaults to the current user and renders their timesheet', async ({ page }) => {
  await page.goto('/?feature=report');

  await expect(page.getByText('Steve Developer - total: 12 h')).toBeVisible();
  await expect(page.getByRole('link', { name: 'EL-1 - Title 1' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Export PDF' })).toBeEnabled();
  // mTest is not selected, so their table is not shown
  await expect(page.getByText('Melanie Test - total: 5 h')).toHaveCount(0);
});

test('seeds scope and users from the query and renders one table per user', async ({ page }) => {
  await page.goto('/?feature=report&scope=elibrary&userIds=sDeveloper,mTest');

  await expect(page.getByText('Steve Developer - total: 12 h')).toBeVisible();
  await expect(page.getByText('Melanie Test - total: 5 h')).toBeVisible();
  await expect(page.getByRole('link', { name: 'EL-2 - Title 2' })).toBeVisible();
  await expect(page.locator('.sd-trigger').first()).toHaveValue('E-Library'); // seeded scope
});

test('user picker search filters the list', async ({ page }) => {
  await page.goto('/?feature=report');

  // The users control is react-sbb-polarion's multi-select: its trigger opens a searchable popup.
  await page.locator('.sd-trigger-multi').click();
  // Each dropdown owns its own portal; the users one is the multi-selectable list.
  const portal = page.locator('.sd-portal').filter({ has: page.locator('[aria-multiselectable="true"]') });
  await portal.locator('.search-box').fill('mel');

  const options = portal.locator('.option');
  await expect(options.filter({ hasText: 'Melanie Test (mTest)' })).toBeVisible();
  await expect(options.filter({ hasText: 'Ayato Seller (aSeller)' })).toHaveCount(0);
});
