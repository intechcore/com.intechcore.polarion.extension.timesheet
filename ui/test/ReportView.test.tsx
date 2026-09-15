import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import ReportView from '../src/components/ReportView';
import { findFeature } from '../src/features';
import type { ScopeInfo, Timesheet, User, WorkItem } from '../src/types';
import { installFetchMock } from './mockFetch';

// The report page as the widget embeds it, and the feature router that picks it. REST is mocked at
// the fetch boundary, so no Polarion is needed.

const USERS: User[] = [
  { id: 'sDeveloper', name: 'Steve Developer' },
  { id: 'mTest', name: 'Melanie Test' },
];
const SCOPES: ScopeInfo[] = [
  { path: '/', name: 'Repository', type: 'root', depth: 0 },
  { path: 'elibrary', name: 'E-Library', type: 'project', depth: 1 },
];
const ITEM: WorkItem = { project: { id: 'elibrary', name: 'elibrary' }, id: 'EL-1', title: 'Title 1' };
const TIMESHEET: Timesheet = {
  startDate: '2026-06-01',
  finishDate: '2026-06-30',
  workRecords: [{ date: '2026-06-01', workItem: ITEM, user: USERS[0], hours: 8 }],
};

const optionRoutes = (timesheet: unknown = TIMESHEET) => [
  { method: 'GET', match: /\/users$/, json: USERS },
  { method: 'GET', match: /\/scopes$/, json: SCOPES },
  { method: 'GET', match: /\/current-user$/, json: USERS[0] },
  { method: 'GET', match: /\/timesheet\?/, json: timesheet },
];

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);
const text = () => document.body.textContent ?? '';

// The report defaults to the current month and now renders a block only for a month that has
// records, so the fixture above has to sit inside the default period. Fake only Date - the real
// timers keep vi.waitFor, userEvent and the mocked fetch working.
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 5, 17)); // 2026-06-17, the month of TIMESHEET
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  setUrl(origUrl);
});

describe('ReportView', () => {
  it('defaults to the current user and shows their timesheet', async () => {
    installFetchMock(optionRoutes());
    render(<ReportView />);

    await vi.waitFor(() => expect(text()).toContain('Steve Developer - total: 8 h'));
    expect(document.querySelector('.timesheet-report h3')?.textContent).toBe('Timesheet report');
    expect(document.querySelector('table')).not.toBeNull();
  });

  it('seeds the scope and the users from the query the widget passes', async () => {
    // The Live Report widget presets these; the report must open on them, not on its own defaults.
    setUrl('?feature=report&scope=elibrary&userIds=sDeveloper,mTest&workingDayInHours=6');
    installFetchMock(optionRoutes());
    render(<ReportView />);

    await vi.waitFor(() => expect(text()).toContain('Melanie Test'));
    // The single-select trigger is an input, so the chosen scope is its value; users become chips.
    expect(document.querySelector<HTMLInputElement>('.sd-trigger')?.value).toBe('E-Library');
    const chips = Array.from(document.querySelectorAll('.sd-chip-label')).map((c) => c.textContent);
    expect(chips.join(' ')).toContain('Steve Developer');
  });

  it('asks the backend for the selected scope and period', async () => {
    setUrl('?scope=elibrary&userIds=sDeveloper');
    const fetchMock = installFetchMock(optionRoutes());
    render(<ReportView />);

    await vi.waitFor(() => {
      const asked = fetchMock.mock.calls.map(([u]) => String(u)).filter((u) => u.includes('/timesheet?'));
      expect(asked.length).toBeGreaterThan(0);
      expect(asked[0]).toContain('scope_path=elibrary');
      expect(asked[0]).toContain('user_ids=sDeveloper');
    });
  });

  it('says when nothing is selected instead of showing an empty grid', async () => {
    installFetchMock([
      { method: 'GET', match: /\/users$/, json: USERS },
      { method: 'GET', match: /\/scopes$/, json: SCOPES },
      // No current user, and the widget passed none: nothing to report on.
      { method: 'GET', match: /\/current-user$/, respond: () => new Response(null, { status: 204 }) },
    ]);
    render(<ReportView />);

    await vi.waitFor(() => expect(text()).toContain('No users selected'));
    expect(document.querySelector('table')).toBeNull();
  });

  it('shows the failure the backend reported', async () => {
    installFetchMock([
      ...optionRoutes().slice(0, 3),
      { method: 'GET', match: /\/timesheet\?/, json: { message: 'scope is unknown' }, status: 400 },
    ]);
    render(<ReportView />);

    await vi.waitFor(() => expect(document.querySelector('.timesheet-error')?.textContent).toBe('scope is unknown'));
  });

  it('adds and removes a user from the report', async () => {
    setUrl('?userIds=sDeveloper');
    installFetchMock(optionRoutes());
    render(<ReportView />);
    await vi.waitFor(() => expect(text()).toContain('Steve Developer - total'));

    // The chip's remove control drops the user again. A full pointer sequence is needed: the shared
    // dropdown listens for it, not for a bare click().
    await userEvent.click(document.querySelector<HTMLElement>('.sd-chip-remove')!);

    await vi.waitFor(() => expect(text()).toContain('No users selected'));
  });

  it('keeps the export disabled while there is nothing to export', async () => {
    installFetchMock([
      { method: 'GET', match: /\/users$/, json: USERS },
      { method: 'GET', match: /\/scopes$/, json: SCOPES },
      { method: 'GET', match: /\/current-user$/, respond: () => new Response(null, { status: 204 }) },
    ]);
    render(<ReportView />);

    await vi.waitFor(() => expect(document.querySelector('.export-pdf-button')).not.toBeNull());
    expect(document.querySelector<HTMLButtonElement>('.export-pdf-button')!.disabled).toBe(true);
  });

  it('enables the export once a user and a period are in play', async () => {
    setUrl('?userIds=sDeveloper');
    installFetchMock(optionRoutes());
    render(<ReportView />);

    await vi.waitFor(() =>
      expect(document.querySelector<HTMLButtonElement>('.export-pdf-button')!.disabled).toBe(false),
    );
  });

  it('exports the current selection to PDF', async () => {
    setUrl('?userIds=sDeveloper');
    installFetchMock(optionRoutes());
    const blobs: Blob[] = [];
    const createObjectURL = URL.createObjectURL.bind(URL);
    vi.spyOn(URL, 'createObjectURL').mockImplementation((obj: Blob | MediaSource) => {
      if (obj instanceof Blob) blobs.push(obj);
      return createObjectURL(obj);
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    render(<ReportView />);
    await vi.waitFor(() =>
      expect(document.querySelector<HTMLButtonElement>('.export-pdf-button')!.disabled).toBe(false),
    );

    document.querySelector<HTMLButtonElement>('.export-pdf-button')!.click();

    // jsPDF is loaded on demand, so the document appears a tick later.
    await vi.waitFor(() => expect(blobs).toHaveLength(1), { timeout: 5000 });
    expect(blobs[0].size).toBeGreaterThan(0);
    vi.restoreAllMocks();
  });

  it('narrows the report when the period is changed', async () => {
    setUrl('?userIds=sDeveloper');
    const fetchMock = installFetchMock(optionRoutes());
    render(<ReportView />);
    await vi.waitFor(() => expect(text()).toContain('Steve Developer - total'));
    const before = fetchMock.mock.calls.length;

    // Assigning .value directly is invisible to React, which tracks the setter.
    const from = document.querySelectorAll<HTMLInputElement>('input[type="date"]')[0];
    await userEvent.fill(from, '2026-06-10');

    await vi.waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(before));
  });
});

describe('feature router', () => {
  it('matches a known feature and answers nothing for the rest', () => {
    expect(findFeature('about')?.id).toBe('about');
    expect(findFeature('report')?.id).toBe('report');
    expect(findFeature('nope')).toBeUndefined();
    expect(findFeature(null)).toBeUndefined();
  });

  it('renders the About page for ?feature=about', async () => {
    setUrl('?feature=about&embedded=true');
    installFetchMock([
      { method: 'GET', match: /\/version$/, json: { bundleName: 'Timesheet', bundleVendor: 'Intechcore GmbH' } },
      { method: 'GET', match: /\/configuration-properties$/, json: { properties: [], obsoleteProperties: [] } },
      { method: 'GET', match: /\/configuration-status/, json: [] },
      { method: 'GET', match: /\/readme$/, respond: () => new Response('<h1>Readme</h1>', { status: 200 }) },
    ]);
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.about-table')).not.toBeNull());
    expect(text()).toContain('Timesheet');
    expect(document.querySelector('.app.standard-admin-page.feature-about')).not.toBeNull();
  });

  it('falls back to the report when no feature is named, as the widget relies on', async () => {
    setUrl('?scope=elibrary');
    installFetchMock(optionRoutes());
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.timesheet-report')).not.toBeNull());
    expect(document.querySelector('.app.feature-report')).not.toBeNull();
  });
});
