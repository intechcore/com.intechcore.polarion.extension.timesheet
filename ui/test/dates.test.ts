import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  chunk,
  currentMonthRange,
  datesInPeriod,
  formatDayMonth,
  formatISO,
  groupDatesByMonth,
  isWeekend,
  parseDate,
} from '../src/utils/dates';

describe('dates utils', () => {
  afterEach(() => vi.useRealTimers());

  it('formatISO / formatDayMonth', () => {
    const d = new Date(2026, 5, 1); // 2026-06-01
    expect(formatISO(d)).toBe('2026-06-01');
    expect(formatDayMonth(d)).toBe('01.06');
  });

  it('parseDate accepts yyyy-MM-dd and yyyyMMdd (local, no tz shift)', () => {
    for (const value of ['2026-06-15', '20260615']) {
      const d = parseDate(value);
      expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 5, 15]);
    }
  });

  it('isWeekend', () => {
    expect(isWeekend(new Date(2026, 0, 3))).toBe(true); // Saturday
    expect(isWeekend(new Date(2026, 0, 4))).toBe(true); // Sunday
    expect(isWeekend(new Date(2026, 0, 5))).toBe(false); // Monday
  });

  it('datesInPeriod is inclusive', () => {
    const dates = datesInPeriod(new Date(2026, 5, 1), new Date(2026, 5, 3));
    expect(dates.map(formatISO)).toEqual(['2026-06-01', '2026-06-02', '2026-06-03']);
  });

  it('currentMonthRange returns first and last day of the current month', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 17)); // 2026-06-17
    expect(currentMonthRange()).toEqual({ start: '2026-06-01', end: '2026-06-30' });
  });

  it('groupDatesByMonth splits across month boundaries', () => {
    const dates = [new Date(2026, 4, 30), new Date(2026, 4, 31), new Date(2026, 5, 1), new Date(2026, 5, 2)];
    const groups = groupDatesByMonth(dates);
    expect(groups).toHaveLength(2);
    expect(groups[0].map(formatISO)).toEqual(['2026-05-30', '2026-05-31']);
    expect(groups[1].map(formatISO)).toEqual(['2026-06-01', '2026-06-02']);
  });

  it('chunk', () => {
    expect(chunk([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
    expect(chunk([], 3)).toEqual([]);
  });
});
