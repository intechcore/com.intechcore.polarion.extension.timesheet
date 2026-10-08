import type React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import UserTimesheet from '../src/components/UserTimesheet';
import { MAX_DAY_WIDTH, MIN_DAY_WIDTH, dayWidth } from '../src/services/useScrollEdges';
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

  it('wraps a long title instead of widening the label column', async () => {
    // `width: max-content` measured the unwrapped titles, so the block with the longest title drew
    // the widest WorkItem column and no two users' tables lined up.
    const long = { ...item('EL-3'), title: 'A work item title long enough to need more than one line '.repeat(3) };
    await show(
      <UserTimesheet
        title="Steve Developer"
        records={[...records, rec('2026-08-04', 2, long)]}
        dates={dates}
        workingDayHours={8}
      />,
    );

    const [june, august] = blocks();
    const label = (table: HTMLTableElement) => table.querySelector('thead th')!.getBoundingClientRect().width;
    const [shortRow, longRow] = [...august.querySelectorAll('tbody tr')].map((tr) => tr.getBoundingClientRect().height);

    expect(label(august)).toBe(label(june));
    expect(longRow).toBeGreaterThan(shortRow);
  });

  it('keeps the WorkItem column in view while the days scroll', async () => {
    await page.viewport(690, 720);
    await show(<UserTimesheet title="Steve Developer" records={records} dates={dates} workingDayHours={8} />);

    const wrap = document.querySelector<HTMLElement>('.timesheet-table-wrap')!;
    const left = () => wrap.querySelector('tbody td')!.getBoundingClientRect().left;
    const before = left();
    wrap.scrollLeft = 300;

    expect(wrap.scrollLeft).toBe(300); // the month is wider than the widget, so it does scroll
    await vi.waitFor(() => expect(left()).toBe(before));
  });

  it('shades the side where days are hidden', async () => {
    await page.viewport(690, 720);
    await show(<UserTimesheet title="Steve Developer" records={records} dates={dates} workingDayHours={8} />);

    const frame = document.querySelector<HTMLElement>('.timesheet-table-frame')!;
    const wrap = frame.querySelector<HTMLElement>('.timesheet-table-wrap')!;
    const shaded = () => ['scrolled-left', 'more-right'].filter((c) => frame.classList.contains(c));

    await vi.waitFor(() => expect(shaded()).toEqual(['more-right']));
    wrap.scrollLeft = 300;
    await vi.waitFor(() => expect(shaded()).toEqual(['scrolled-left', 'more-right']));
    wrap.scrollLeft = wrap.scrollWidth;
    await vi.waitFor(() => expect(shaded()).toEqual(['scrolled-left']));
  });

  it('scrolls the days with a bar that starts after the WorkItem column', async () => {
    await page.viewport(690, 720);
    await show(<UserTimesheet title="Steve Developer" records={records} dates={dates} workingDayHours={8} />);

    const wrap = document.querySelector<HTMLElement>('.timesheet-table-wrap')!;
    const bar = document.querySelector<HTMLElement>('.timesheet-scrollbar')!;
    const label = wrap.querySelector('th')!.getBoundingClientRect();

    await vi.waitFor(() => expect(bar.hidden).toBe(false));
    expect(bar.getBoundingClientRect().left).toBeCloseTo(label.right, 0);
    expect(bar.getBoundingClientRect().right).toBeCloseTo(wrap.getBoundingClientRect().right, 0);
    // The table hides its own scrollbar, and has no vertical one: the page scrolls it.
    expect(wrap.offsetHeight).toBe(wrap.clientHeight);
    expect(getComputedStyle(wrap).overflowY).toBe('hidden');

    bar.scrollLeft = 200;
    await vi.waitFor(() => expect(wrap.scrollLeft).toBe(200));
    wrap.scrollLeft = 350;
    await vi.waitFor(() => expect(bar.scrollLeft).toBe(350));
    // The two scroll the same distance: the end of the bar is the end of the month.
    bar.scrollLeft = bar.scrollWidth;
    await vi.waitFor(() => expect(wrap.scrollLeft + wrap.clientWidth).toBeCloseTo(wrap.scrollWidth, 0));
  });

  it('shades nothing when the month fits', async () => {
    await page.viewport(1280, 720);
    const week = datesInPeriod(parseDate('2026-06-01'), parseDate('2026-06-07'));
    await show(<UserTimesheet title="Steve Developer" records={records} dates={week} workingDayHours={8} />);

    expect(document.querySelector('.timesheet-table-frame')!.className).toBe('timesheet-table-frame');
    expect(document.querySelector<HTMLElement>('.timesheet-scrollbar')!.hidden).toBe(true);
  });

  describe('the width of a day', () => {
    // September and October 2026: 30 and 31 days.
    const autumn = datesInPeriod(parseDate('2026-09-01'), parseDate('2026-10-31'));
    const autumnRecords = [rec('2026-09-15', 2, JUNE_ITEM), rec('2026-10-15', 4, JUNE_ITEM)];
    const tables = () => [...document.querySelectorAll<HTMLElement>('.timesheet-table-wrap')];
    const dayOf = (wrap: HTMLElement) => wrap.querySelectorAll('thead th')[1].getBoundingClientRect().width;
    const overflow = (wrap: HTMLElement) => wrap.scrollWidth - wrap.clientWidth;

    it('narrows the days so that a month which nearly fits fits', async () => {
      // 31 days of 48px and the 412px label need 2241px: 2000px used to scroll by 261px.
      await page.viewport(2000, 720);
      await show(<UserTimesheet title="Steve Developer" records={autumnRecords} dates={autumn} workingDayHours={8} />);

      await vi.waitFor(() => expect(tables().map(overflow)).toEqual([0, 0]));
      // The bar follows once the narrower days have reached the table.
      await vi.waitFor(() =>
        expect(document.querySelectorAll<HTMLElement>('.timesheet-scrollbar:not([hidden])')).toHaveLength(0),
      );
      // Both blocks keep the same days, so the 30-day month is the shorter table.
      expect(dayOf(tables()[0])).toBe(dayOf(tables()[1]));
      expect(tables()[0].querySelector('table')!.getBoundingClientRect().width).toBeLessThan(
        tables()[1].querySelector('table')!.getBoundingClientRect().width,
      );
    });

    it('keeps a day at 48px on a wide widget', async () => {
      await page.viewport(2600, 720);
      await show(<UserTimesheet title="Steve Developer" records={autumnRecords} dates={autumn} workingDayHours={8} />);

      await vi.waitFor(() => expect(dayOf(tables()[1])).toBe(MAX_DAY_WIDTH + 11));
    });

    it('scrolls at the narrowest day when the month does not fit', async () => {
      await page.viewport(1280, 720);
      await show(<UserTimesheet title="Steve Developer" records={autumnRecords} dates={autumn} workingDayHours={8} />);

      await vi.waitFor(() => expect(dayOf(tables()[1])).toBe(MIN_DAY_WIDTH + 11));
      expect(overflow(tables()[1])).toBeGreaterThan(0);
    });

    it('fills the scroller with the longest month, between the narrowest and the widest day', () => {
      expect(dayWidth(2000, 412, 31)).toBe(Math.floor((2000 - 412) / 31) - 11);
      expect(dayWidth(5000, 412, 31)).toBe(MAX_DAY_WIDTH);
      expect(dayWidth(800, 412, 31)).toBe(MIN_DAY_WIDTH);
    });
  });

  it('says so instead of drawing empty grids when the user booked nothing', async () => {
    await show(<UserTimesheet title="Ayato Seller" records={[]} dates={dates} workingDayHours={8} />);

    expect(blocks()).toHaveLength(0);
    expect(document.querySelector('.timesheet-empty')?.textContent).toBe('- no work records in this period -');
  });
});
