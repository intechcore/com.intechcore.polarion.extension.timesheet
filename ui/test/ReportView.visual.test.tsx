import type React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page, userEvent } from 'vitest/browser';
import App from '../src/App';
import ReportView from '../src/components/ReportView';
import TimesheetBlock from '../src/components/TimesheetBlock';
import UserTimesheet from '../src/components/UserTimesheet';
import type { ScopeInfo, Timesheet, User, WorkItem, WorkRecord } from '../src/types';
import { datesInPeriod, parseDate } from '../src/utils/dates';
import { installFetchMock } from './mockFetch';
import type { Route } from './mockFetch';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// Visual-regression states for the report. Kept separate from the behavior tests (Docker-only, since
// any toMatchScreenshot file diffs on non-Linux font antialiasing). References live in
// test/expected/ReportView/ and MUST be generated in Docker (npm run test:update:docker).
//
// The report is the product surface - it renders inside the widget's iframe - so what is pinned here
// is the control row and the timesheet grid, the two things a styling change would silently move.

const USERS: User[] = [
  { id: 'sDeveloper', name: 'Steve Developer' },
  { id: 'mTest', name: 'Melanie Test' },
];
const SCOPES: ScopeInfo[] = [
  { path: '/', name: 'Repository', type: 'root', depth: 0 },
  { path: 'elibrary', name: 'E-Library', type: 'project', depth: 1 },
];
const ITEM: WorkItem = { project: { id: 'elibrary', name: 'elibrary' }, id: 'EL-1', title: 'Write the report' };
const record = (date: string, hours: number): WorkRecord => ({ date, workItem: ITEM, user: USERS[0], hours });
const TIMESHEET: Timesheet = {
  startDate: '2026-06-01',
  finishDate: '2026-06-30',
  workRecords: [record('2026-06-01', 8), record('2026-06-02', 4)],
};

// The tree the scope popup draws: the root, a group, and projects at two depths.
const SCOPE_TREE: ScopeInfo[] = [
  { path: '/', name: 'Repository (all projects)', type: 'root', depth: 0 },
  { path: '/Demo Projects', name: 'Demo Projects', type: 'group', depth: 1 },
  { path: 'drivepilot', name: 'Drive Pilot', type: 'project', depth: 2 },
  { path: 'elibrary', name: 'E-Library', type: 'project', depth: 2 },
  { path: 'library', name: 'Document Library', type: 'project', depth: 1 },
];
const workItem = (id: string, title: string): WorkItem => ({ ...ITEM, id, title });
const SECOND_ITEM = workItem('EL-2', 'Review the REST API documentation');
// Two users, so the page shows one heading and one block per user.
const TWO_USERS: Timesheet = {
  ...TIMESHEET,
  workRecords: [
    ...TIMESHEET.workRecords,
    { date: '2026-06-03', workItem: SECOND_ITEM, user: USERS[1], hours: 6 },
    { date: '2026-06-15', workItem: ITEM, user: USERS[1], hours: 2 },
  ],
};

/** The REST answers of a report, with any route replaced by the caller's. */
function reportRoutes(overrides: Route[] = [], timesheet: Timesheet = TIMESHEET): Route[] {
  return [
    ...overrides,
    { method: 'GET', match: /\/users$/, json: USERS },
    { method: 'GET', match: /\/scopes$/, json: SCOPES },
    { method: 'GET', match: /\/current-user$/, json: USERS[0] },
    { method: 'GET', match: /\/timesheet\?/, json: timesheet },
  ];
}

/** The report at the query the widget passes, in June 2026, the month of the fixtures. */
function openReport(search: string, routes: Route[] = reportRoutes()) {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 5, 17));
  window.history.replaceState({}, '', search);
  installFetchMock(routes);
  render(<App />);
}

/** Sizes the viewport to the page, so the capture holds all of it and nothing more. */
async function pageShot(name: string, width = 1280) {
  const app = document.querySelector('.app') as HTMLElement;
  await settleLayout();
  await page.viewport(width, Math.ceil(app.scrollHeight) + 40);
  await settleBeforeCapture();
  await expect(page.elementLocator(app)).toMatchScreenshot(name);
}

/**
 * Opens a picker and captures it with its popup. The popup is a fixed portal on <body>, outside the
 * page, so the page is stretched under it: an element capture takes the pixels of its box, popup
 * included.
 */
async function popupShot(trigger: string, name: string) {
  const app = document.querySelector('.app') as HTMLElement;
  await page.viewport(1280, 480);
  app.style.minHeight = '460px';
  await userEvent.click(document.querySelector<HTMLElement>(trigger)!);
  await vi.waitFor(() =>
    expect(
      [...document.querySelectorAll<HTMLElement>('.sd-portal .options')].some((o) => o.getClientRects().length > 0),
    ).toBe(true),
  );
  // The pointer stays on the trigger: parking it would move it over the popup.
  await settleBeforeCapture(false);
  await expect(page.elementLocator(app)).toMatchScreenshot(name);
}

/** The report in the app shell at the default viewport, for a capture of its control row. */
function openControls(search: string) {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 5, 17));
  window.history.replaceState({}, '', search);
  installFetchMock(reportRoutes());
  render(inAppShell(<ReportView />));
}

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

const shot = (selector: string, name: string) =>
  expect(page.elementLocator(document.querySelector(selector) as HTMLElement)).toMatchScreenshot(name);

/**
 * Renders inside the shell App.tsx puts around every page. Without it the --sbb-* control tokens do
 * not resolve and the shared controls paint unstyled, which is not what the app shows.
 */
const inAppShell = (ui: React.ReactNode) => <div className="app standard-admin-page">{ui}</div>;

describe.skipIf(!__PIXEL_REFERENCES__)('ReportView visual states', () => {
  it('control row: scope, user chips, period and export', async () => {
    // The period defaults to the current month, so the reference image would rot every month.
    // Fake only Date - the real timers keep vi.waitFor and the mocked fetch working.
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 5, 17)); // 2026-06-17, the month of TIMESHEET
    window.history.replaceState({}, '', '?scope=elibrary&userIds=sDeveloper');
    installFetchMock([
      { method: 'GET', match: /\/users$/, json: USERS },
      { method: 'GET', match: /\/scopes$/, json: SCOPES },
      { method: 'GET', match: /\/current-user$/, json: USERS[0] },
      { method: 'GET', match: /\/timesheet\?/, json: TIMESHEET },
    ]);
    render(inAppShell(<ReportView />));

    await vi.waitFor(() => expect(document.querySelector('.timesheet-controls')).not.toBeNull());
    await vi.waitFor(() => expect(document.body.textContent).toContain('Steve Developer - total'));
    // Without it the capture took whatever state the test files before it left: a pointer over a
    // picker, or a frame not yet painted.
    await settleBeforeCapture();
    await shot('.timesheet-controls', 'report-controls');
  });

  it('month blocks of different lengths keep the same column widths', async () => {
    // February is the short month that used to break the layout: under the automatic table layout it
    // handed its spare width to the WorkItem column and drew it twice as wide as the next month's.
    const feb = datesInPeriod(parseDate('2026-02-01'), parseDate('2026-02-28'));
    const mar = datesInPeriod(parseDate('2026-03-01'), parseDate('2026-03-31'));

    render(
      inAppShell(
        <UserTimesheet
          title="Steve Developer"
          records={[record('2026-02-02', 8), record('2026-03-02', 4)]}
          dates={[...feb, ...mar]}
          workingDayHours={8}
        />,
      ),
    );

    await vi.waitFor(() => expect(document.querySelectorAll('table.timesheet')).toHaveLength(2));
    await shot('.user-timesheet', 'month-blocks');
  });

  it('timesheet grid with a weekend column and a filled day', async () => {
    const monday = new Date(2026, 5, 1);
    const saturday = new Date(2026, 5, 6);
    render(
      inAppShell(
        <TimesheetBlock
          workItems={[ITEM]}
          records={[record('2026-06-01', 8)]}
          dates={[monday, saturday]}
          workingDayHours={8}
        />,
      ),
    );

    await vi.waitFor(() => expect(document.querySelector('table')).not.toBeNull());
    await shot('table', 'timesheet-block');
  });

  it('a long title wraps and the WorkItem column stays put while the days scroll', async () => {
    const long: WorkItem = {
      ...ITEM,
      id: 'EL-2',
      title: 'Information about variables in the Administration page is incomplete compared to the release before',
    };
    render(
      inAppShell(
        <UserTimesheet
          title="Steve Developer"
          records={[record('2026-06-01', 8), { ...record('2026-06-30', 4), workItem: long }]}
          dates={datesInPeriod(parseDate('2026-06-01'), parseDate('2026-06-30'))}
          workingDayHours={8}
        />,
      ),
    );

    await vi.waitFor(() => expect(document.querySelector('table')).not.toBeNull());
    const wrap = document.querySelector<HTMLElement>('.timesheet-table-wrap')!;
    wrap.scrollLeft = wrap.scrollWidth; // the end of the month, with the WorkItem column still in view
    await settleLayout();
    await shot('.user-timesheet', 'sticky-workitem');
  });

  it('control row: a scope locked to the page', async () => {
    openControls('?scope=elibrary&scopeLocked=true&userIds=sDeveloper');
    await vi.waitFor(() => expect(document.body.textContent).toContain('Steve Developer - total'));
    await settleBeforeCapture();
    await shot('.timesheet-controls', 'controls-scope-locked');
  });

  it('control row: the user locked to the viewer', async () => {
    openControls('?scope=elibrary&userLocked=true');
    await vi.waitFor(() => expect(document.body.textContent).toContain('Steve Developer - total'));
    await settleBeforeCapture();
    await shot('.timesheet-controls', 'controls-user-locked');
  });

  it('control row: a custom period', async () => {
    openControls('?scope=elibrary&userIds=sDeveloper&period=custom&from=2026-03-02&to=2026-04-15');
    await vi.waitFor(() => expect(document.body.textContent).toContain('Steve Developer - total'));
    await settleBeforeCapture();
    await shot('.timesheet-controls', 'controls-custom-period');
  });

  it('a user with no records in the period', async () => {
    render(
      inAppShell(
        <UserTimesheet
          title="Melanie Test"
          records={[]}
          dates={datesInPeriod(parseDate('2026-06-01'), parseDate('2026-06-30'))}
          workingDayHours={8}
        />,
      ),
    );

    await vi.waitFor(() => expect(document.querySelector('.timesheet-empty')).not.toBeNull());
    await settleBeforeCapture();
    await shot('.user-timesheet', 'user-empty');
  });

  it('the work item as Polarion renders it: icon, id and title', async () => {
    const icon =
      'data:image/svg+xml,' +
      encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" rx="3" fill="#8e44ad"/></svg>',
      );
    const native: WorkItem = {
      ...ITEM,
      html:
        '<span class="polarion-JSWikiRenderer"><a class="polarion-Hyperlink" href="#">' +
        `<img class="polarion-Icons" src="${icon}" alt="Task"><span>EL-1</span><span> - Write the report</span></a></span>`,
    };
    const resolved: WorkItem = {
      ...SECOND_ITEM,
      html:
        '<span class="polarion-JSWikiRenderer"><a class="polarion-Hyperlink" href="#">' +
        `<img class="polarion-Icons" src="${icon}" alt="Task">` +
        '<span style="text-decoration: line-through">EL-2</span><span> - Review the REST API documentation</span></a></span>',
    };
    render(
      inAppShell(
        <TimesheetBlock
          workItems={[native, resolved]}
          records={[
            { ...record('2026-06-01', 8), workItem: native },
            { ...record('2026-06-02', 3), workItem: resolved },
          ]}
          dates={[new Date(2026, 5, 1), new Date(2026, 5, 2)]}
          workingDayHours={8}
        />,
      ),
    );

    await vi.waitFor(() => expect(document.querySelectorAll('img.polarion-Icons')).toHaveLength(2));
    await settleBeforeCapture();
    await shot('table', 'native-workitem');
  });

  /**
   * The page as the widget embeds it: App puts the `.app standard-admin-page feature-report` shell
   * around ReportView, and that shell is what the component captures above cannot show.
   *
   * It runs LAST on purpose. It is the only case here that resizes the viewport (the others inherit the
   * instance default from vitest.config.ts) and that parks the pointer, and both of those outlive a
   * test - the whole file shares one browser page. Put it first and every reference below it is
   * captured under a layout it was not generated with.
   */
  it('the whole report page, through the feature router', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 5, 17)); // 2026-06-17, the month of TIMESHEET
    window.history.replaceState({}, '', '?feature=report&embedded=true&scope=elibrary&userIds=sDeveloper');
    installFetchMock([
      { method: 'GET', match: /\/users$/, json: USERS },
      { method: 'GET', match: /\/scopes$/, json: SCOPES },
      { method: 'GET', match: /\/current-user$/, json: USERS[0] },
      { method: 'GET', match: /\/timesheet\?/, json: TIMESHEET },
    ]);
    render(<App />);

    await vi.waitFor(() => expect(document.body.textContent).toContain('Steve Developer - total'));
    const app = document.querySelector('.app') as HTMLElement;
    await settleLayout();
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await settleBeforeCapture();
    await expect(page.elementLocator(app)).toMatchScreenshot('report-page');
  });

  // Every case below sizes the viewport itself, so their order among each other does not matter.

  it('the page with the controls hidden: tables only', async () => {
    openReport(
      '?feature=report&scope=elibrary&userIds=sDeveloper,mTest&hideControls=true',
      reportRoutes([], TWO_USERS),
    );
    await vi.waitFor(() => expect(document.body.textContent).toContain('Melanie Test - total'));
    await pageShot('report-page-bare');
  });

  it('the page in a column of a multi-column Live Report', async () => {
    openReport('?feature=report&scope=elibrary&userIds=sDeveloper,mTest', reportRoutes([], TWO_USERS));
    await vi.waitFor(() => expect(document.body.textContent).toContain('Melanie Test - total'));
    await pageShot('report-page-narrow', 690);
  });

  it('the page with no user selected', async () => {
    // No current user is known, and the widget names none.
    openReport(
      '?feature=report&scope=elibrary',
      reportRoutes([{ method: 'GET', match: /\/current-user$/, respond: () => new Response(null, { status: 204 }) }]),
    );
    await vi.waitFor(() => expect(document.body.textContent).toContain('No users selected'));
    await pageShot('report-no-users');
  });

  it('the page when the backend refuses the request', async () => {
    openReport(
      '?feature=report&scope=elibrary&userIds=sDeveloper',
      reportRoutes([
        {
          method: 'GET',
          match: /\/timesheet\?/,
          json: { message: 'Scope path holds characters which are not allowed' },
          status: 400,
        },
      ]),
    );
    await vi.waitFor(() => expect(document.querySelector('.timesheet-error')).not.toBeNull());
    await pageShot('report-error');
  });

  it('the scope popup: the tree with its icons', async () => {
    openReport(
      '?feature=report&scope=elibrary&userIds=sDeveloper',
      reportRoutes([{ method: 'GET', match: /\/scopes$/, json: SCOPE_TREE }]),
    );
    await vi.waitFor(() => expect(document.body.textContent).toContain('Steve Developer - total'));
    await popupShot('.control-scope .sd-trigger', 'scope-popup');
  });

  it('the user popup: checkboxes for the selection', async () => {
    openReport('?feature=report&scope=elibrary&userIds=sDeveloper');
    await vi.waitFor(() => expect(document.body.textContent).toContain('Steve Developer - total'));
    await popupShot('.control-users .sd-trigger-multi', 'users-popup');
  });
});
