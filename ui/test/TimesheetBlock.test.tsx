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
});
