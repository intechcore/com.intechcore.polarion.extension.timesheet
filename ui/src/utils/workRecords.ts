import type { WorkItem, WorkRecord } from '../types';
import { formatISO } from './dates';

export const workItemKey = (wi: WorkItem): string => `${wi.project.id}/${wi.id}`;
export const workItemUrl = (wi: WorkItem): string => `/polarion/#/project/${wi.project.id}/workitem?id=${wi.id}`;

export const formatHours = (hours: number): string => (hours > 0 ? `${hours} h` : '');

export function uniqueWorkItems(records: WorkRecord[]): WorkItem[] {
  const map = new Map<string, WorkItem>();
  records.forEach((r) => map.set(workItemKey(r.workItem), r.workItem));
  return [...map.values()];
}

// The records that fall inside one block's days. A block lists the work items it actually has
// records for, so a month the user did not book on that item shows no row for it.
export function recordsWithin(records: WorkRecord[], dates: Date[]): WorkRecord[] {
  const days = new Set(dates.map(formatISO));
  return records.filter((r) => days.has(r.date));
}

export function groupByUser(records: WorkRecord[]): Map<string, WorkRecord[]> {
  const map = new Map<string, WorkRecord[]>();
  records.forEach((r) => {
    const list = map.get(r.user.id) ?? [];
    list.push(r);
    map.set(r.user.id, list);
  });
  return map;
}

export function sumHours(records: WorkRecord[], iso: string, key?: string): number {
  return records
    .filter((r) => r.date === iso && (key === undefined || workItemKey(r.workItem) === key))
    .reduce((acc, r) => acc + r.hours, 0);
}

export const totalHours = (records: WorkRecord[]): number => records.reduce((acc, r) => acc + r.hours, 0);
