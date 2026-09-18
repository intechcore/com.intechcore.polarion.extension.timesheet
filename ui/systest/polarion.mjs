/**
 * What the system suite needs from a running Polarion: a signed-in page, and the fixed data the
 * report is asserted against.
 *
 * The data lives in an existing project, in a period far enough away that it cannot collide with
 * real records. Work records are created for every run and removed after it. Work items are not:
 * Polarion's REST API refuses to delete one, so they carry a marker in their title and are reused.
 */
import { request as playwrightRequest } from '@playwright/test';

export const BASE_URL = process.env.POLARION_URL || 'http://localhost';
export const TOKEN = process.env.POLARION_TOKEN;
export const PROJECT = process.env.POLARION_SYSTEST_PROJECT || 'elibrary';

const USER = process.env.POLARION_USER || 'admin';
const PASSWORD = process.env.POLARION_PASSWORD || 'admin';

const MARKER = 'systest timesheet';
export const PERIOD = { start: '2030-03-04', end: '2030-03-08' };

export async function signIn(page) {
  await page.goto(`${BASE_URL}/polarion/`, { waitUntil: 'domcontentloaded' });
  if (await page.locator('#j_username').count()) {
    await page.fill('#j_username', USER);
    await page.fill('#j_password', PASSWORD);
    await page.press('#j_password', 'Enter');
    await page.waitForLoadState('domcontentloaded');
  }
  await page.waitForTimeout(3000);
}

/** REST v1 takes a token and nothing else: a session is answered with 401. */
export async function v1() {
  return playwrightRequest.newContext({
    // The trailing slash matters: a relative path would otherwise replace the last segment.
    baseURL: `${BASE_URL}/polarion/rest/v1/`,
    extraHTTPHeaders: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
  });
}

async function workItemFor(api, title) {
  const query = encodeURIComponent(`title:"${title}"`);
  const found = await (
    await api.get(`projects/${PROJECT}/workitems?query=${query}&fields[workitems]=id,title&page[size]=10`)
  ).json();
  const match = (found.data || []).find((item) => item.attributes?.title === title);
  if (match) return match.id.split('/')[1];

  const created = await api.post(`projects/${PROJECT}/workitems`, {
    data: { data: [{ type: 'workitems', attributes: { type: 'task', title, status: 'open' } }] },
  });
  if (!created.ok()) throw new Error(`Creating "${title}" answered ${created.status()}: ${await created.text()}`);
  return (await created.json()).data[0].id.split('/')[1];
}

// Everything the fixtures book sits in 2030, this suite and the Java one alike, which is what tells
// their records from anybody else's on the same work item.
const FIXTURE_YEAR = '2030-';

/**
 * Removes the records of the fixture, and only those. Deleting whatever else hangs on the work item
 * would take a booking that belongs to someone else; keeping just the ids of this run instead would
 * leave behind what an interrupted run wrote, which is what the exact assertions then trip over.
 */
async function clearRecords(api, workItem, users) {
  const response = await api.get(
    `projects/${PROJECT}/workitems/${workItem}/workrecords?fields[workrecords]=date,user&page[size]=100`,
  );
  if (!response.ok()) return;
  for (const record of (await response.json()).data || []) {
    const date = String(record.attributes?.date ?? '');
    const user = record.relationships?.user?.data?.id;
    // The fixture year and one of the two users it books for. A booking of anybody else, on the same
    // work item and in the same year, is none of this suite's business.
    if (!date.startsWith(FIXTURE_YEAR) || !users.includes(user)) continue;
    await api.delete(`projects/${PROJECT}/workitems/${workItem}/workrecords/${record.id.split('/')[2]}`);
  }
}

/**
 * Puts the project into the state the report is asserted against: two users, one work week, a shape
 * that is the same on every run.
 */
export async function seed(users) {
  const api = await v1();
  const first = await workItemFor(api, `${MARKER} one`);
  const second = await workItemFor(api, `${MARKER} two`);
  await clearRecords(api, first, users);
  await clearRecords(api, second, users);

  // Polarion writes a duration as "3d 1/2h": halves are a fraction, not minutes. The two users get
  // different totals, 15.5 and 12 hours, so a report that mixes them up cannot pass.
  const records = [
    [first, users[0], '2030-03-04', '8h'],
    [first, users[0], '2030-03-05', '4h'],
    [second, users[0], '2030-03-05', '3 1/2h'],
    [second, users[1], '2030-03-06', '6h'],
    [first, users[1], '2030-03-07', '2h'],
    [second, users[1], '2030-03-08', '4h'],
  ];
  for (const [workItem, user, date, timeSpent] of records) {
    const created = await api.post(`projects/${PROJECT}/workitems/${workItem}/workrecords`, {
      data: {
        data: [
          {
            type: 'workrecords',
            attributes: { date, timeSpent },
            relationships: { user: { data: { type: 'users', id: user } } },
          },
        ],
      },
    });
    if (!created.ok()) throw new Error(`Adding a record of ${user} on ${date} answered ${created.status()}`);
  }
  await api.dispose();
  return { first, second };
}

export async function removeSeededRecords(users) {
  const api = await v1();
  for (const title of [`${MARKER} one`, `${MARKER} two`]) {
    await clearRecords(api, await workItemFor(api, title), users);
  }
  await api.dispose();
}

/** The users the report is asserted for, as the extension lists them. */
export async function twoUsers(page) {
  const response = await page.request.get(`${BASE_URL}/polarion/timesheet/rest/internal/users`);
  const listed = await response.json();
  if (listed.length < 2) throw new Error('The instance needs two enabled users');
  return listed.slice(0, 2).map((user) => user.id);
}

/** The report as the widget opens it, with the period the fixture covers. */
export async function openReport(page, users) {
  const url =
    `${BASE_URL}/polarion/timesheet-app/ui/app/index.html?feature=report` +
    `&scope=${encodeURIComponent(PROJECT)}&userIds=${encodeURIComponent(users.join(','))}&workingDayInHours=8`;
  await page.goto(url, { waitUntil: 'domcontentloaded' });
  await page.locator('input[type="date"]').first().waitFor({ state: 'visible', timeout: 60000 });
  const dates = page.locator('input[type="date"]');
  await dates.nth(0).fill(PERIOD.start);
  await dates.nth(1).fill(PERIOD.end);
  // A filled date input keeps the caret in one of its segments, and the highlight would be part of
  // every screenshot taken afterwards.
  await page.evaluate(() => document.activeElement instanceof HTMLElement && document.activeElement.blur());
  await page.waitForTimeout(4000);
}
