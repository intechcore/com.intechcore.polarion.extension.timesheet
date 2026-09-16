import { describe, expect, it } from 'vitest';
import type { WorkRecord } from '../src/types';
import {
  formatHours,
  groupByUser,
  recordsWithin,
  sumHours,
  totalHours,
  uniqueWorkItems,
  workItemKey,
  workItemUrl,
} from '../src/utils/workRecords';

const wi = (id: string, project = 'elibrary', title = id) => ({ project: { id: project, name: project }, id, title });
const rec = (date: string, itemId: string, userId: string, hours: number): WorkRecord => ({
  date,
  workItem: wi(itemId),
  user: { id: userId, name: userId },
  hours,
});

const records: WorkRecord[] = [
  rec('2026-06-01', 'EL-1', 'sDeveloper', 8),
  rec('2026-06-01', 'EL-2', 'sDeveloper', 2),
  rec('2026-06-02', 'EL-1', 'sDeveloper', 6),
  rec('2026-06-01', 'EL-1', 'mTest', 4),
];

describe('workRecords utils', () => {
  it('workItemKey / workItemUrl', () => {
    expect(workItemKey(wi('EL-1'))).toBe('elibrary/EL-1');
    expect(workItemUrl(wi('EL-1'))).toBe('/polarion/#/project/elibrary/workitem?id=EL-1');
  });

  it('formatHours', () => {
    expect(formatHours(0)).toBe('');
    expect(formatHours(8)).toBe('8 h');
  });

  it('sumHours by date, and by date+workItem', () => {
    expect(sumHours(records, '2026-06-01')).toBe(14); // 8 + 2 + 4
    expect(sumHours(records, '2026-06-01', 'elibrary/EL-1')).toBe(12); // 8 + 4
    expect(sumHours(records, '2026-06-03')).toBe(0);
  });

  it('uniqueWorkItems dedupes by key', () => {
    expect(uniqueWorkItems(records).map(workItemKey).sort()).toEqual(['elibrary/EL-1', 'elibrary/EL-2']);
  });

  it('recordsWithin keeps only the records booked on the given days', () => {
    const june1 = new Date(2026, 5, 1);
    const june3 = new Date(2026, 5, 3);

    expect(recordsWithin(records, [june1])).toHaveLength(3);
    expect(recordsWithin(records, [june1, june3])).toHaveLength(3); // nothing booked on the 3rd
    expect(recordsWithin(records, [june3])).toEqual([]);
  });

  it('groupByUser', () => {
    const byUser = groupByUser(records);
    expect(byUser.get('sDeveloper')).toHaveLength(3);
    expect(byUser.get('mTest')).toHaveLength(1);
  });

  it('totalHours', () => {
    expect(totalHours(records)).toBe(20);
    expect(totalHours([])).toBe(0);
  });
});
