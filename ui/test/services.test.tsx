import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page, userEvent } from 'vitest/browser';
import ReportView from '../src/components/ReportView';
import useIframeAutoHeight from '../src/services/useIframeAutoHeight';
import useRemote from '../src/services/useRemote';
import useReportOptions from '../src/services/useReportOptions';
import useTimesheet from '../src/services/useTimesheet';
import type { ScopeInfo, Timesheet, User } from '../src/types';
import { installFetchMock } from './mockFetch';

// The data layer: how requests are addressed, and what each hook exposes while and after they answer.
// Hooks are exercised through a probe component, which is how they run in the app.

const USERS: User[] = [{ id: 'sDeveloper', name: 'Steve Developer' }];
const SCOPES: ScopeInfo[] = [{ path: '/', name: 'Repository', type: 'root', depth: 0 }];
const TIMESHEET: Timesheet = { startDate: '2026-06-01', finishDate: '2026-06-30', workRecords: [] };

/** Renders a hook and exposes its latest value. */
function probe<T>(useHook: () => T) {
  const seen: { current: T | undefined } = { current: undefined };
  function Probe() {
    seen.current = useHook();
    return <div data-testid="probe" />;
  }
  render(<Probe />);
  return seen;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('useRemote', () => {
  it('addresses the session endpoints under the extension REST path', async () => {
    const fetchMock = installFetchMock([{ method: 'GET', match: /\/users$/, json: USERS }]);
    const remote = probe(useRemote);

    await vi.waitFor(() => expect(remote.current).toBeDefined());
    const response = await remote.current!.sendRequest({ method: 'GET', url: '/users' });

    expect(response.ok).toBe(true);
    // Without a dev token the app talks to /internal, which Polarion authenticates by session.
    expect(String(fetchMock.mock.calls[0][0])).toBe('/polarion/timesheet/rest/internal/users');
  });

  it('sets the content type only when one is given', async () => {
    const fetchMock = installFetchMock([{ method: 'PUT', match: /./, json: {} }]);
    const remote = probe(useRemote);
    await vi.waitFor(() => expect(remote.current).toBeDefined());

    await remote.current!.sendRequest({ method: 'PUT', url: '/x', body: '{}', contentType: 'application/json' });
    expect((fetchMock.mock.calls[0][1]!.headers as Record<string, string>)['Content-Type']).toBe('application/json');

    await remote.current!.sendRequest({ method: 'PUT', url: '/x' });
    expect(fetchMock.mock.calls[1][1]!.headers).toEqual({});
  });

  it('answers a network failure as a 503 response rather than rejecting', async () => {
    // Callers branch on response.ok; a rejected promise would surface as an unhandled error instead.
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new Error('offline'))),
    );
    const remote = probe(useRemote);
    await vi.waitFor(() => expect(remote.current).toBeDefined());

    const response = await remote.current!.sendRequest({ method: 'GET', url: '/users' });

    expect(response.status).toBe(503);
    expect((await response.json()).message).toContain('Be sure Polarion is started');
  });
});

describe('useReportOptions', () => {
  it('loads the users, the scopes and the current user', async () => {
    installFetchMock([
      { method: 'GET', match: /\/users$/, json: USERS },
      { method: 'GET', match: /\/scopes$/, json: SCOPES },
      { method: 'GET', match: /\/current-user$/, json: USERS[0] },
    ]);
    const options = probe(useReportOptions);

    await vi.waitFor(() => expect(options.current!.currentUserId).toBe('sDeveloper'));
    expect(options.current!.users).toEqual(USERS);
    expect(options.current!.scopes).toEqual(SCOPES);
  });

  it('leaves the lists empty when the requests fail, without throwing', async () => {
    installFetchMock([
      { method: 'GET', match: /\/users$/, json: {}, status: 500 },
      { method: 'GET', match: /\/scopes$/, json: {}, status: 500 },
      { method: 'GET', match: /\/current-user$/, json: {}, status: 500 },
    ]);
    const options = probe(useReportOptions);

    await vi.waitFor(() => expect(options.current).toBeDefined());
    expect(options.current!.users).toEqual([]);
    expect(options.current!.scopes).toEqual([]);
    expect(options.current!.currentUserId).toBeNull();
  });

  it('treats an empty current-user answer as nobody', async () => {
    // 204 is what the backend sends when the session has no Polarion user.
    installFetchMock([
      { method: 'GET', match: /\/users$/, json: USERS },
      { method: 'GET', match: /\/scopes$/, json: SCOPES },
      { method: 'GET', match: /\/current-user$/, respond: () => new Response(null, { status: 204 }) },
    ]);
    const options = probe(useReportOptions);

    await vi.waitFor(() => expect(options.current!.users).toEqual(USERS));
    expect(options.current!.currentUserId).toBeNull();
  });
});

describe('useTimesheet', () => {
  const params = { userIds: ['sDeveloper'], startDate: '2026-06-01', endDate: '2026-06-30', scopePath: 'elibrary' };

  it('requests the selection and reports the result', async () => {
    const fetchMock = installFetchMock([{ method: 'GET', match: /\/timesheet\?/, json: TIMESHEET }]);
    const state = probe(() => useTimesheet(params));

    await vi.waitFor(() => expect(state.current!.timesheet).toEqual(TIMESHEET));
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('user_ids=sDeveloper');
    expect(url).toContain('start_date=2026-06-01');
    expect(url).toContain('scope_path=elibrary');
    expect(state.current!.fetching).toBe(false);
    expect(state.current!.error).toBeNull();
  });

  it('asks for nothing until a user and a period are chosen', async () => {
    const fetchMock = installFetchMock([{ method: 'GET', match: /\/timesheet\?/, json: TIMESHEET }]);
    const state = probe(() => useTimesheet({ ...params, userIds: [] }));

    await vi.waitFor(() => expect(state.current).toBeDefined());
    expect(state.current!.timesheet).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('surfaces the message the backend sent', async () => {
    installFetchMock([{ method: 'GET', match: /\/timesheet\?/, json: { message: 'scope is unknown' }, status: 400 }]);
    const state = probe(() => useTimesheet(params));

    await vi.waitFor(() => expect(state.current!.error).toBe('scope is unknown'));
  });

  it('falls back to the status when the failure carries no message', async () => {
    installFetchMock([{ method: 'GET', match: /\/timesheet\?/, respond: () => new Response('nope', { status: 500 }) }]);
    const state = probe(() => useTimesheet(params));

    await vi.waitFor(() => expect(state.current!.error).toBe('Request failed with status 500'));
  });
});

describe('useIframeAutoHeight', () => {
  it('posts the content height to the embedding widget', async () => {
    const posted: { msg: unknown; origin: string }[] = [];
    const original = window.parent.postMessage.bind(window.parent);
    vi.spyOn(window.parent, 'postMessage').mockImplementation(((msg: unknown, origin: string) => {
      posted.push({ msg, origin });
      return original(msg as never, origin as never);
    }) as typeof window.parent.postMessage);

    probe(useIframeAutoHeight);

    await vi.waitFor(() => expect(posted.length).toBeGreaterThan(0));
    expect(posted[0].msg).toMatchObject({ type: 'timesheet-app-height' });
    expect((posted[0].msg as { height: number }).height).toBeGreaterThanOrEqual(0);
    // Addressed to this origin, never to '*': a page embedding the app from elsewhere gets nothing.
    expect(posted[0].origin).toBe(window.location.origin);
  });

  // The browser context is shared by every test file, so the viewport this suite shrinks has to go
  // back to the one the pixel references were captured at.
  afterEach(() => page.viewport(1280, 720));

  it('covers an open picker popup, which the iframe would otherwise cut off', async () => {
    // The popup is a `position: fixed` portal on <body>: it adds nothing to body.scrollHeight and
    // does not resize body, so the height posted before this fix left the option list clipped at
    // whatever height the iframe already had.
    //
    // A short viewport is what the widget's iframe is: the report is a few controls tall, the popup
    // opens past the bottom of it. At the default 720px the page is taller than the popup and the
    // bug cannot show at all.
    await page.viewport(1000, 260);
    const posted: { height: number }[] = [];
    vi.spyOn(window.parent, 'postMessage').mockImplementation(((msg: { height: number }) => {
      posted.push(msg);
    }) as typeof window.parent.postMessage);
    // Enough projects for the list to reach its own max height, as a real repository would.
    const scopes: ScopeInfo[] = [
      ...SCOPES,
      ...Array.from({ length: 12 }, (_, i) => ({
        path: `p${i}`,
        name: `Project ${i}`,
        type: 'project' as const,
        depth: 1,
      })),
    ];
    installFetchMock([
      { method: 'GET', match: /\/users$/, json: USERS },
      { method: 'GET', match: /\/scopes$/, json: scopes },
      { method: 'GET', match: /\/current-user$/, json: USERS[0] },
      { method: 'GET', match: /\/timesheet\?/, json: TIMESHEET },
    ]);
    render(
      <div className="app standard-admin-page">
        <ReportView />
      </div>,
    );
    await vi.waitFor(() => expect(document.querySelector('.control-scope .sd-trigger')).not.toBeNull());

    // The trigger opens on mousedown, so a bare .click() would not open the popup at all.
    await userEvent.click(document.querySelector<HTMLElement>('.control-scope .sd-trigger')!);

    const portal = () => document.querySelector<HTMLElement>('.sd-portal .options')!;
    await vi.waitFor(() => expect(portal().getClientRects().length).toBeGreaterThan(0));
    await vi.waitFor(() => {
      const bottom = portal().getBoundingClientRect().bottom;
      expect(posted.at(-1)!.height).toBeGreaterThanOrEqual(bottom);
      // The popup really does reach past the page, or this would pass for the wrong reason.
      expect(bottom).toBeGreaterThan(document.body.scrollHeight);
    });
  });
});
