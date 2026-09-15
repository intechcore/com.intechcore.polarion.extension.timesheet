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

export function currentMonthRange(): { start: string; end: string } {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  return { start: formatISO(new Date(y, m, 1)), end: formatISO(new Date(y, m + 1, 0)) };
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
