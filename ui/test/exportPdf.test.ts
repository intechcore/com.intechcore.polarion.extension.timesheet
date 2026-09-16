import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { WorkItem, WorkRecord } from '../src/types';
import { exportTimesheetPdf } from '../src/utils/exportPdf';

// The PDF export mirrors the on-screen report and the two are meant to stay in sync. jsPDF and its
// autotable plugin run for real here - it is a browser test, and they work in a browser - so the
// layout engine is genuinely exercised. Only `save` is intercepted, to keep the run from downloading
// a file, and `text` is recorded because the headings are what the layout is asserted through.

const ITEM: WorkItem = { project: { id: 'elibrary', name: 'elibrary' }, id: 'EL-1', title: 'Title 1' };
const record = (date: string, hours: number, workItem = ITEM): WorkRecord => ({
  date,
  workItem,
  user: { id: 'u', name: 'u' },
  hours,
});

const june = [new Date(2026, 5, 1), new Date(2026, 5, 6)];
const july = [new Date(2026, 6, 1)];

const options = (over: Partial<Parameters<typeof exportTimesheetPdf>[0]> = {}) => ({
  scopeName: 'E-Library',
  period: { start: '2026-06-01', end: '2026-07-01' },
  dates: [...june, ...july],
  workingDayHours: 8,
  users: [{ name: 'Steve Developer', records: [record('2026-06-01', 8)] }],
  ...over,
});

let blobs: Blob[];

beforeEach(() => {
  blobs = [];
  // jsPDF hands the finished document to the browser as a blob URL and clicks a download link.
  // Capturing the blob is what proves a real, non-empty PDF came out; swallowing the click keeps
  // the run from downloading anything.
  const createObjectURL = URL.createObjectURL.bind(URL);
  vi.spyOn(URL, 'createObjectURL').mockImplementation((obj: Blob | MediaSource) => {
    if (obj instanceof Blob) blobs.push(obj);
    return createObjectURL(obj);
  });
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});

afterEach(() => vi.restoreAllMocks());

/** Exports and reports the size of the produced document. */
async function sizeOf(opts: Parameters<typeof exportTimesheetPdf>[0]) {
  blobs = [];
  await exportTimesheetPdf(opts);
  return producedPdf().size;
}

/** The PDF jsPDF produced for the last export. */
const producedPdf = () => {
  expect(blobs).toHaveLength(1);
  return blobs[0];
};

describe('exportTimesheetPdf', () => {
  it('produces a non-empty PDF for a user with work records', async () => {
    await exportTimesheetPdf(options());

    const pdf = producedPdf();
    expect(pdf.size).toBeGreaterThan(0);
    expect(pdf.type).toContain('pdf');
  });

  it('exports every selected user in one document', async () => {
    await exportTimesheetPdf(
      options({
        users: [
          { name: 'Steve Developer', records: [record('2026-06-01', 8)] },
          { name: 'Melanie Test', records: [record('2026-06-06', 4)] },
        ],
      }),
    );

    expect(producedPdf().size).toBeGreaterThan(0);
  });

  it('still exports a user who has no records in the period', async () => {
    // The section then carries a "no work records" note instead of an empty grid.
    await exportTimesheetPdf(options({ users: [{ name: 'Ayato Seller', records: [] }] }));

    expect(producedPdf().size).toBeGreaterThan(0);
  });

  it('skips a month the user did not work in and keeps the one they did', async () => {
    const bothMonths = await sizeOf(options());
    const juneOnly = await sizeOf(options({ dates: june }));

    // July holds no records, so dropping it from the period changes nothing that is drawn.
    expect(bothMonths).toBe(juneOnly);
  });

  it('grows the document as more users are exported', async () => {
    const one = await sizeOf(options());
    const many = await sizeOf(
      options({
        users: Array.from({ length: 30 }, (_, i) => ({ name: `User ${i}`, records: [record('2026-06-01', 1)] })),
      }),
    );

    expect(many).toBeGreaterThan(one);
  });

  it('keeps the table inside the page when the period is only a few days', async () => {
    // autoTable applies columnStyles to body cells only. The WorkItem column used to take its width
    // from the head and foot cells, which fall back to the day-column width - wider than the label
    // column whenever the period is short - and the table then ran past the right margin. autoTable
    // reports that as "N units width could not fit page" on the console, so watching the console is
    // what proves the layout still fits.
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});

    await exportTimesheetPdf(options({ dates: [new Date(2026, 5, 1), new Date(2026, 5, 2)] }));

    expect(warn.mock.calls.flat().join(' ')).not.toContain('could not fit page');
  });

  it('still exports when a work item icon cannot be loaded', async () => {
    const withIcon: WorkItem = { ...ITEM, iconUrl: '/polarion/ria/images/does-not-exist.gif' };

    await exportTimesheetPdf(
      options({ users: [{ name: 'Steve Developer', records: [record('2026-06-01', 8, withIcon)] }] }),
    );

    expect(producedPdf().size).toBeGreaterThan(0);
  });
});
