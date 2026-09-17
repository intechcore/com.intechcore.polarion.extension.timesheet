import type React from 'react';
import { screen } from '@testing-library/dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import TimesheetBlock from '../src/components/TimesheetBlock';
import type { WorkItem, WorkRecord } from '../src/types';

const item = (id: string, title: string, html?: string): WorkItem => ({
  project: { id: 'elibrary', name: 'elibrary' },
  id,
  title,
  html,
});
const rec = (date: string, hours: number, wi: WorkItem): WorkRecord => ({
  date,
  workItem: wi,
  user: { id: 'u', name: 'u' },
  hours,
});

// vitest-browser-react mounts asynchronously, so every render is awaited before the DOM is read.
async function show(ui: React.ReactElement) {
  render(ui);
  await vi.waitFor(() => expect(document.body.textContent).not.toBe(''));
}

describe('TimesheetBlock', () => {
  afterEach(cleanup);

  const el1 = item('EL-1', 'Title 1');
  const monday = new Date(2026, 5, 1); // 2026-06-01
  const saturday = new Date(2026, 5, 6); // 2026-06-06
  const records = [rec('2026-06-01', 8, el1)];

  it('renders day headers, the work item row, hours and the total', async () => {
    await show(<TimesheetBlock workItems={[el1]} records={records} dates={[monday, saturday]} workingDayHours={8} />);
    expect(screen.getByText('01.06')).toBeInTheDocument();
    expect(screen.getByText('06.06')).toBeInTheDocument();
    expect(screen.getByText('EL-1 - Title 1')).toBeInTheDocument(); // fallback link (no html)
    expect(screen.getByText('Total: 8 h')).toBeInTheDocument();
    expect(screen.getAllByText('8 h').length).toBeGreaterThanOrEqual(1);
  });

  it('marks weekend day columns', async () => {
    await show(<TimesheetBlock workItems={[el1]} records={records} dates={[monday, saturday]} workingDayHours={8} />);
    expect(screen.getByText('06.06').className).toContain('weekend');
    expect(screen.getByText('01.06').className).not.toContain('weekend');
  });

  it('renders the native work item HTML when provided instead of the fallback link', async () => {
    const native = item('EL-2', 'Native', '<a class="polarion-Hyperlink">EL-2 native</a>');
    await show(
      <TimesheetBlock
        workItems={[native]}
        records={[rec('2026-06-01', 2, native)]}
        dates={[monday]}
        workingDayHours={8}
      />,
    );
    expect(screen.getByText('EL-2 native')).toBeInTheDocument();
    expect(screen.queryByText('EL-2 - Native')).not.toBeInTheDocument();
  });

  it('keeps the icon and the link Polarion rendered', async () => {
    const rendered = item(
      'EL-2',
      'Native',
      '<span class="polarion-JSWikiRenderer"><img src="/polarion/icons/default/workitem.svg" alt="Task">' +
        '<a href="/polarion/#/project/elibrary/workitem?id=EL-2">EL-2 - Native</a></span>',
    );
    await show(
      <TimesheetBlock
        workItems={[rendered]}
        records={[rec('2026-06-01', 2, rendered)]}
        dates={[monday]}
        workingDayHours={8}
      />,
    );

    expect(document.querySelector('img')?.getAttribute('src')).toBe('/polarion/icons/default/workitem.svg');
    expect(document.querySelector('td a')?.getAttribute('href')).toBe('/polarion/#/project/elibrary/workitem?id=EL-2');
  });

  // Polarion escapes what it renders, so this is the second barrier: whatever arrives through the
  // REST API is written into the DOM, and it must not be able to run.
  it('strips script, event handlers and javascript links from the rendered HTML', async () => {
    const flag = window as unknown as Record<string, unknown>;
    delete flag.timesheetXssFlag;
    const hostile = item(
      'EL-3',
      'Hostile',
      '<a href="javascript:void(window.timesheetXssFlag = true)" onclick="window.timesheetXssFlag = true">EL-3</a>' +
        '<img src="missing.png" onerror="window.timesheetXssFlag = true">' +
        '<script>window.timesheetXssFlag = true;</script>',
    );
    await show(
      <TimesheetBlock
        workItems={[hostile]}
        records={[rec('2026-06-01', 2, hostile)]}
        dates={[monday]}
        workingDayHours={8}
      />,
    );

    const cell = document.querySelector('tbody td');
    expect(cell?.querySelector('script')).toBeNull();
    expect(cell?.querySelector('[onclick]')).toBeNull();
    expect(cell?.querySelector('[onerror]')).toBeNull();
    expect(cell?.querySelector('a')?.getAttribute('href')).toBeNull();
    expect(screen.getByText('EL-3')).toBeInTheDocument();
    await vi.waitFor(() => expect(flag.timesheetXssFlag).toBeUndefined());
  });
});
