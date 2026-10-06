export const pad = (n: number): string => String(n).padStart(2, '0');
export const formatISO = (d: Date): string => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const formatDayMonth = (d: Date): string => `${pad(d.getDate())}.${pad(d.getMonth() + 1)}`;
export const isWeekend = (d: Date): boolean => d.getDay() === 0 || d.getDay() === 6;

export function chunk<T>(items: T[], size: number): T[][] {
  const blocks: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    blocks.push(items.slice(i, i + size));
  }
  return blocks;
}

// The report is split into one block per calendar month.
export function groupDatesByMonth(dates: Date[]): Date[][] {
  const groups: Date[][] = [];
  let current: Date[] = [];
  let key = '';
  for (const date of dates) {
    const monthKey = `${date.getFullYear()}-${date.getMonth()}`;
    if (monthKey !== key) {
      if (current.length) groups.push(current);
      current = [];
      key = monthKey;
    }
    current.push(date);
  }
  if (current.length) groups.push(current);
  return groups;
}

// Accepts "yyyy-MM-dd" or "yyyyMMdd"; builds a local Date (no timezone shift).
export function parseDate(value: string): Date {
  const compact = value.replace(/-/g, '');
  return new Date(Number(compact.slice(0, 4)), Number(compact.slice(4, 6)) - 1, Number(compact.slice(6, 8)));
}

export interface Period {
  start: string;
  end: string;
}

/** A calendar month counted from the current one: 0 is this month, -1 the one before. */
export function monthRange(offset = 0): Period {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth() + offset;
  return { start: formatISO(new Date(y, m, 1)), end: formatISO(new Date(y, m + 1, 0)) };
}

export function currentMonthRange(): Period {
  return monthRange(0);
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * The period the widget asks for. A custom period needs both dates, in order; anything else, a widget
 * saved before the period existed included, is the current month.
 */
export function widgetPeriod(period: string | null, from: string | null, to: string | null): Period {
  if (period === 'previous-month') return monthRange(-1);
  if (period === 'custom' && from && to && ISO_DATE.test(from) && ISO_DATE.test(to) && from <= to) {
    return { start: from, end: to };
  }
  return monthRange(0);
}

export function datesInPeriod(start: Date, end: Date): Date[] {
  const dates: Date[] = [];
  const current = new Date(start);
  while (current <= end) {
    dates.push(new Date(current));
    current.setDate(current.getDate() + 1);
  }
  return dates;
}
