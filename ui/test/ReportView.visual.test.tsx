import type React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import ReportView from '../src/components/ReportView';
import TimesheetBlock from '../src/components/TimesheetBlock';
import UserTimesheet from '../src/components/UserTimesheet';
import type { ScopeInfo, Timesheet, User, WorkItem, WorkRecord } from '../src/types';
import { datesInPeriod, parseDate } from '../src/utils/dates';
import { installFetchMock } from './mockFetch';
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
});
