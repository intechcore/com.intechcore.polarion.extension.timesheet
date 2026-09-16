import type React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import UserTimesheet from '../src/components/UserTimesheet';
import type { WorkItem, WorkRecord } from '../src/types';
import { datesInPeriod, parseDate } from '../src/utils/dates';

// What each month block is allowed to contain. Reporting a long period used to repeat every work
// item of the whole period in every month's table and to draw a table for months with no work at
// all, which is what these assertions pin down.

const item = (id: string): WorkItem => ({ project: { id: 'elibrary', name: 'elibrary' }, id, title: `Title ${id}` });
const rec = (date: string, hours: number, wi: WorkItem): WorkRecord => ({
  date,
  workItem: wi,
  user: { id: 'u', name: 'u' },
  hours,
});

const JUNE_ITEM = item('EL-1');
const AUGUST_ITEM = item('EL-2');
// June, July and August 2026 - the middle month holds no work.
const dates = datesInPeriod(parseDate('2026-06-01'), parseDate('2026-08-31'));
const records = [rec('2026-06-02', 8, JUNE_ITEM), rec('2026-08-03', 4, AUGUST_ITEM)];

async function show(ui: React.ReactElement) {
  render(ui);
  await vi.waitFor(() => expect(document.body.textContent).not.toBe(''));
}

const blocks = () => [...document.querySelectorAll<HTMLTableElement>('table.timesheet')];
const rowLabels = (table: HTMLTableElement) =>
  [...table.querySelectorAll('tbody tr td:first-child')].map((td) => td.textContent);

describe('UserTimesheet', () => {
  afterEach(cleanup);
  // The browser context is shared by every test file, so a resized viewport has to go back to the
  // one the pixel references were captured at.
  afterEach(() => page.viewport(1280, 720));

  it('draws a block only for the months that hold records', async () => {
    await show(<UserTimesheet title="Steve Developer" records={records} dates={dates} workingDayHours={8} />);

    expect(blocks()).toHaveLength(2); // June and August, not July
    expect(document.body.textContent).toContain('01.06');
    expect(document.body.textContent).toContain('01.08');
    expect(document.body.textContent).not.toContain('01.07');
  });

  it('lists in each block only the work items booked in that month', async () => {
    await show(<UserTimesheet title="Steve Developer" records={records} dates={dates} workingDayHours={8} />);

    expect(rowLabels(blocks()[0])).toEqual(['EL-1 - Title EL-1']);
    expect(rowLabels(blocks()[1])).toEqual(['EL-2 - Title EL-2']);
  });

  it('keeps the period total in the heading, across every month', async () => {
    await show(<UserTimesheet title="Steve Developer" records={records} dates={dates} workingDayHours={8} />);

    expect(document.querySelector('h4')?.textContent).toBe('Steve Developer - total: 12 h');
  });

  it('gives every month block the same column widths, whatever its length', async () => {
    // The automatic table layout used to cap each table at the container and share the leftover out
    // between its columns, so a short month drew a wider WorkItem column than a long one.
    await show(<UserTimesheet title="Steve Developer" records={records} dates={dates} workingDayHours={8} />);

    const widthsOf = (table: HTMLTableElement) =>
      [...table.querySelectorAll('thead th')].slice(0, 2).map((th) => th.getBoundingClientRect().width);
    const [june, august] = blocks();

    expect(june.querySelectorAll('thead th')).toHaveLength(31); // 30 June days + the label column
    expect(august.querySelectorAll('thead th')).toHaveLength(32);
    expect(widthsOf(june)).toEqual(widthsOf(august));
    // The label column keeps the width the stylesheet asks for instead of absorbing the spare room.
    expect(getComputedStyle(june.querySelector('thead th')!).width).toBe('400px');
    // ... and it stays wider than a day column, which is what a short month used to break.
    expect(widthsOf(june)[0]).toBeGreaterThan(widthsOf(june)[1]);
  });

  it('gives the label column less room when the widget is narrow', async () => {
    // A widget dropped into a column of a multi-column Live Report gets about 690px. A fixed 400px
    // label spent two thirds of that on one column and pushed every day off screen.
    await page.viewport(690, 720);
    await show(<UserTimesheet title="Steve Developer" records={records} dates={dates} workingDayHours={8} />);

    const label = (table: HTMLTableElement) => table.querySelector('thead th')!.getBoundingClientRect().width;
    const [june, august] = blocks();

    expect(label(june)).toBeLessThan(400);
    // Narrow or not, the blocks still line up with each other.
    expect(label(june)).toBe(label(august));
  });

  it('says so instead of drawing empty grids when the user booked nothing', async () => {
    await show(<UserTimesheet title="Ayato Seller" records={[]} dates={dates} workingDayHours={8} />);

    expect(blocks()).toHaveLength(0);
    expect(document.querySelector('.timesheet-empty')?.textContent).toBe('- no work records in this period -');
  });
});
