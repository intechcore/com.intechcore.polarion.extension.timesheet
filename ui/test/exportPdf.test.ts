import { jsPDF } from 'jspdf';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Mock } from 'vitest';
import type { WorkItem, WorkRecord } from '../src/types';
import { formatISO } from '../src/utils/dates';
import { exportTimesheetPdf, labelLines } from '../src/utils/exportPdf';

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

  describe('work item icons', () => {
    // The tests serve stand-ins at Polarion's image paths (vitest.config.ts).
    const ICON = '/polarion/ria/images/dle/operations/actionPdfExport16.svg';
    const exportWith = (iconUrl: string) =>
      exportTimesheetPdf(
        options({ users: [{ name: 'Steve Developer', records: [record('2026-06-01', 8, { ...ITEM, iconUrl })] }] }),
      );

    it('draws the icon of a work item before its title', async () => {
      const addImage = vi.spyOn(jsPDF.API, 'addImage');

      await exportWith(ICON);

      expect(addImage).toHaveBeenCalledOnce();
      expect(String(addImage.mock.calls[0][0])).toMatch(/^data:image\/png;base64,/);
    });

    it('leaves the icon out when the browser cannot draw it, and still exports', async () => {
      const addImage = vi.spyOn(jsPDF.API, 'addImage');
      vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
      await exportWith(ICON);

      vi.restoreAllMocks();
      const addImageAgain = vi.spyOn(jsPDF.API, 'addImage');
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
      vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockImplementation(() => {
        throw new DOMException('tainted', 'SecurityError');
      });
      await exportWith(ICON);

      expect(addImage).not.toHaveBeenCalled();
      expect(addImageAgain).not.toHaveBeenCalled();
    });

    it('still exports when jsPDF refuses the icon', async () => {
      vi.spyOn(jsPDF.API, 'addImage').mockImplementation(() => {
        throw new Error('unsupported image');
      });

      await exportWith(ICON);

      expect(producedPdf().size).toBeGreaterThan(0);
    });
  });

  it('starts a new page before a month table that would begin at the foot of the page', async () => {
    // One user, four work items in every month of a year: twelve tables in a row, so one of them ends
    // within reach of the foot of a page and the next one moves to a new page.
    const year = Array.from({ length: 365 }, (_, i) => new Date(2026, 0, 1 + i));
    const records = Array.from({ length: 12 }, (_, m) =>
      [1, 2, 3, 4].map((n) => record(formatISO(new Date(2026, m, n + 2)), 2, { ...ITEM, id: `EL-${n}` })),
    ).flat();

    await exportTimesheetPdf(
      options({
        dates: year,
        period: { start: '2026-01-01', end: '2026-12-31' },
        users: [{ name: 'Steve Developer', records }],
      }),
    );

    const pages = (await producedPdf().text()).match(/\/Type \/Page\b(?!s)/g) ?? [];
    expect(pages.length).toBeGreaterThan(1);
  });

  it('starts a new page when the next table would not fit', async () => {
    const users = Array.from({ length: 20 }, (_, i) => ({ name: `User ${i}`, records: [record('2026-06-01', 8)] }));

    await exportTimesheetPdf(options({ users }));

    // One page object per page; "/Type /Pages" is the page tree.
    const pages = (await producedPdf().text()).match(/\/Type \/Page\b(?!s)/g) ?? [];
    expect(pages.length).toBeGreaterThan(1);
  });

  describe('work item label', () => {
    const ICON = '/polarion/ria/images/dle/operations/actionPdfExport16.svg';
    const LONG: WorkItem = {
      ...ITEM,
      id: 'DEV-13645',
      title:
        'DeleteDummyWorkitemsHook: one unresolvable link aborts the whole check and lets a protected workitem be deleted',
      iconUrl: ICON,
    };
    const exportItem = (workItem: WorkItem) =>
      exportTimesheetPdf(
        options({ users: [{ name: 'Steve Developer', records: [record('2026-06-01', 8, workItem)] }] }),
      );
    // jsPDF defines text, setTextColor and line on each document rather than on jsPDF.API, so the spies
    // go on the document as jsPDF creates it: its "initialized" event hands the handler the document.
    let doc: { text: Mock; setTextColor: Mock; line: Mock };
    const onInitialized: [string, (this: jsPDF) => void] = [
      'initialized',
      function (this: jsPDF) {
        doc = {
          text: vi.spyOn(this, 'text') as unknown as Mock,
          setTextColor: vi.spyOn(this, 'setTextColor') as unknown as Mock,
          line: vi.spyOn(this, 'line') as unknown as Mock,
        };
      },
    ];
    const events = (jsPDF.API as unknown as { events: unknown[] }).events;
    beforeEach(() => events.push(onInitialized));
    afterEach(() => events.splice(events.indexOf(onInitialized), 1));
    const textCalls = () =>
      doc.text.mock.calls.map(([value, x, y]) => ({ value: String(value), x: Number(x), y: Number(y) }));

    it('starts the first line after the icon and the next lines under it, as on screen', async () => {
      const addImage = vi.spyOn(jsPDF.API, 'addImage');

      await exportItem(LONG);

      const id = textCalls().find((c) => c.value === 'DEV-13645')!;
      const wrapped = textCalls().filter((c) => c.y > id.y && c.y < id.y + 30 && !/^\d|Total/.test(c.value));
      const [, , iconX, iconY, , iconHeight] = addImage.mock.calls[0] as unknown as number[];
      expect(wrapped.length).toBeGreaterThan(0);
      // The wrapped lines start where the icon does, left of the id.
      expect(wrapped.every((c) => Math.abs(c.x - iconX) < 0.01)).toBe(true);
      expect(id.x).toBeGreaterThan(iconX);
      // The icon stands on the first line, not in the middle of the cell.
      expect(iconY).toBeLessThan(id.y);
      expect(iconY + iconHeight).toBeGreaterThan(id.y - 4);
    });

    it('writes the id in black and the title in the blue of the report', async () => {
      await exportItem({ ...ITEM, title: 'Title 1' });

      // The color in force when a text is written: the last one set before it.
      const colorOf = (value: string) => {
        const at = doc.text.mock.invocationCallOrder[doc.text.mock.calls.findIndex(([v]) => v === value)];
        const before = doc.setTextColor.mock.invocationCallOrder.filter((n) => n < at).length - 1;
        return doc.setTextColor.mock.calls[before];
      };
      expect(colorOf('EL-1')).toEqual([0, 0, 0]);
      expect(colorOf(' - Title 1')).toEqual([25, 127, 162]);
    });

    it('strikes the id of a resolved work item through, and only then', async () => {
      // The struck lines: level, starting at the id and a little above its baseline.
      const struck = () => {
        const id = textCalls().find((c) => c.value === 'EL-1')!;
        return doc.line.mock.calls.filter(([x1, y1, , y2]) => x1 === id.x && y1 === y2 && y1 < id.y && y1 > id.y - 8);
      };

      await exportItem({ ...ITEM });
      expect(struck()).toHaveLength(0);

      await exportItem({ ...ITEM, resolved: true });
      expect(struck()).toHaveLength(1);
    });
  });
});

describe('labelLines', () => {
  // One unit per character: the widths are easy to count.
  const measure = (text: string) => text.length;

  it('keeps a short label on one line, the id apart from the title', () => {
    expect(labelLines({ id: 'EL-1', title: 'Title 1' }, 40, 0, measure)).toEqual([
      [
        { text: 'EL-1', kind: 'id' },
        { text: ' - Title 1', kind: 'title' },
      ],
    ]);
  });

  it('wraps at words, with a first line shorter by the icon', () => {
    // "EL-1 - aaaa" is 11 wide: it fits 12, but not 12 less an indent of 2.
    expect(labelLines({ id: 'EL-1', title: 'aaaa bbbb' }, 12, 2, measure)).toEqual([
      [
        { text: 'EL-1', kind: 'id' },
        { text: ' -', kind: 'title' },
      ],
      [{ text: 'aaaa bbbb', kind: 'title' }],
    ]);
  });

  it('gives a word longer than a line a line of its own', () => {
    expect(labelLines({ id: 'EL-1', title: 'abcdefghijkl' }, 8, 0, measure)).toEqual([
      [
        { text: 'EL-1', kind: 'id' },
        { text: ' -', kind: 'title' },
      ],
      [{ text: 'abcdefghijkl', kind: 'title' }],
    ]);
  });
});
