import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page, userEvent } from 'vitest/browser';
import DateRangePicker from '../src/components/DateRangePicker';
import ExportPdfButton from '../src/components/ExportPdfButton';
import ReportView from '../src/components/ReportView';
import useRemote from '../src/services/useRemote';
import { installFetchMock } from './mockFetch';

// The remaining edges of the small pieces: the date range while it is still empty, and the dev-token
// mode of the REST hook, which only `vite dev` ever runs.

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const dateInputs = () => Array.from(document.querySelectorAll<HTMLInputElement>('input[type="date"]'));

const reportRoutes = [
  { method: 'GET', match: /\/users$/, json: [{ id: 'admin', name: 'System Administrator' }] },
  { method: 'GET', match: /\/scopes$/, json: [{ path: '/', name: 'Repository', type: 'root', depth: 0 }] },
  { method: 'GET', match: /\/current-user$/, json: { id: 'admin', name: 'System Administrator' } },
  { method: 'GET', match: /\/timesheet\?/, json: { startDate: '', finishDate: '', workRecords: [] } },
];

describe('DateRangePicker', () => {
  it('bounds each end of the range by the other', async () => {
    render(
      <DateRangePicker startDate="2026-06-01" endDate="2026-06-30" onStartChange={() => {}} onEndChange={() => {}} />,
    );

    await vi.waitFor(() => expect(dateInputs()).toHaveLength(2));
    const [from, to] = dateInputs();
    expect(from.max).toBe('2026-06-30');
    expect(to.min).toBe('2026-06-01');
  });

  it('leaves both ends unbounded while the range is empty', async () => {
    // An empty string would be an invalid min/max, so the component passes undefined instead.
    render(<DateRangePicker startDate="" endDate="" onStartChange={() => {}} onEndChange={() => {}} />);

    await vi.waitFor(() => expect(dateInputs()).toHaveLength(2));
    const [from, to] = dateInputs();
    expect(from.max).toBe('');
    expect(to.min).toBe('');
  });

  it('reports each end as it is changed', async () => {
    const onStartChange = vi.fn();
    const onEndChange = vi.fn();
    render(
      <DateRangePicker
        startDate="2026-06-01"
        endDate="2026-06-30"
        onStartChange={onStartChange}
        onEndChange={onEndChange}
      />,
    );
    await vi.waitFor(() => expect(dateInputs()).toHaveLength(2));

    await userEvent.fill(dateInputs()[0], '2026-06-10');
    await userEvent.fill(dateInputs()[1], '2026-06-20');

    expect(onStartChange).toHaveBeenCalledWith('2026-06-10');
    expect(onEndChange).toHaveBeenCalledWith('2026-06-20');
  });
});

describe('the control row', () => {
  // Scope, Users and the two period fields are three different controls - RSP's single and multi
  // select and a native date input - and each brought its own height: the date inputs kept the
  // browser's default because RSP styles every input type but `date`, and the multi-select padded
  // its chip row out past the rest. They have to read as one row, at the height of the button.
  // The browser context is shared by every test file, so the viewport this suite resizes has to go
  // back to the one the pixel references were captured at.
  afterEach(() => page.viewport(1280, 720));

  it('gives every field the same height', async () => {
    installFetchMock(reportRoutes);
    // The RSP control styles are scoped to the page shell App.tsx renders every page inside.
    render(
      <div className="app standard-admin-page">
        <ReportView />
      </div>,
    );

    // Waits for the user chip, which is what used to make the multi-select the tallest of the four.
    await vi.waitFor(() => expect(document.querySelector('.sd-chip')).not.toBeNull());
    const fields = [
      document.querySelector('.control-scope .sd-trigger')!,
      document.querySelector('.control-users .sd-trigger-multi')!,
      ...dateInputs(),
    ];
    const boxes = fields.map((el) => el.getBoundingClientRect());

    // 28px: the row runs at the button's height, not Polarion's 23px form-control height.
    expect(boxes.map((b) => b.height)).toEqual(boxes.map(() => 28));
    // Same line, not just the same size.
    expect(new Set(boxes.map((b) => b.top)).size).toBe(1);
    expect(document.querySelector('.export-pdf-button')!.getBoundingClientRect().height).toBe(28);
  });

  it('widens the pickers into the free space without breaking the row', async () => {
    // The pickers are the controls that run out of room - a scope path, or a second user chip - so
    // they take what the period fields and the button leave over. The widget is embedded in a Live
    // Report, though, and at the narrow end that whole row still has to stay on one line.
    installFetchMock(reportRoutes);
    render(
      <div className="app standard-admin-page">
        <ReportView />
      </div>,
    );
    await vi.waitFor(() => expect(document.querySelector('.sd-chip')).not.toBeNull());

    const width = (sel: string) => document.querySelector(sel)!.getBoundingClientRect().width;
    const lines = () =>
      new Set([...document.querySelectorAll('.timesheet-controls .control')].map((c) => c.getBoundingClientRect().top))
        .size;

    // Both grow past the basis the stylesheet gives them, the users picker faster.
    expect(width('.control-scope .searchable-dropdown')).toBeGreaterThan(240);
    expect(width('.control-users .searchable-dropdown')).toBeGreaterThan(width('.control-scope .searchable-dropdown'));
    expect(lines()).toBe(1);

    // 995px is the width the report renders at in a full-page Live Report - the tightest real case.
    await page.viewport(995, 720);
    await vi.waitFor(() => expect(window.innerWidth).toBe(995));
    expect(lines()).toBe(1);
    expect(width('.control-scope .searchable-dropdown')).toBeGreaterThanOrEqual(240);
  });

  it('keeps the export button on the shared toolbar height', async () => {
    render(
      <div className="app standard-admin-page">
        <ExportPdfButton scopeName="" period={{ start: '', end: '' }} dates={[]} workingDayHours={8} users={[]} />
      </div>,
    );

    await vi.waitFor(() => expect(document.querySelector('.export-pdf-button')).not.toBeNull());
    // 28px is RSP's --sbb-btn-height. The dialog button (sbb-btn--primary) has no fixed height and
    // grew to 35px, which towered over the 23px fields beside it.
    expect(document.querySelector('.export-pdf-button')!.getBoundingClientRect().height).toBe(28);
  });
});

describe('useRemote with a development token', () => {
  it('switches to the token-authenticated endpoints', async () => {
    // VITE_BEARER_TOKEN is a `vite dev` convenience; production builds force it undefined, so this
    // path never ships - but it is the one a developer runs against a real Polarion.
    vi.stubEnv('VITE_BEARER_TOKEN', 'secret-token');
    const fetchMock = installFetchMock([{ method: 'GET', match: /./, json: {} }]);

    let remote: ReturnType<typeof useRemote> | undefined;
    function Probe() {
      remote = useRemote();
      return null;
    }
    render(<Probe />);
    await vi.waitFor(() => expect(remote).toBeDefined());

    await remote!.sendRequest({ method: 'GET', url: '/users' });

    expect(String(fetchMock.mock.calls[0][0])).toBe('/polarion/timesheet/rest/api/users');
    expect((fetchMock.mock.calls[0][1]!.headers as Record<string, string>).Authorization).toBe('Bearer secret-token');
  });
});
