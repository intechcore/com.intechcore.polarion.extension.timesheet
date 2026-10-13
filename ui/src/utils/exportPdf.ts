import { jsPDF } from 'jspdf';
// The package's ESM entry. The default CommonJS one resolves to the module namespace rather than the
// function under Vite's browser-mode transform, so the module could not be loaded in a test at all.
import autoTable from 'jspdf-autotable/es';
import type { WorkItem, WorkRecord } from '../types';
import { chunk, formatDayMonth, formatISO, groupDatesByMonth, isWeekend } from './dates';
import {
  formatHours,
  recordsWithin,
  sumHours,
  totalHours,
  uniqueWorkItems,
  workItemKey,
  workItemUrl,
} from './workRecords';

// jsPDF's built-in proportional font (has normal/bold/italic) - Polarion's standard look.
const PDF_FONT = 'helvetica';

export interface PdfUserSection {
  name: string;
  records: WorkRecord[];
}

export interface ExportOptions {
  scopeName: string;
  period: { start: string; end: string };
  dates: Date[];
  workingDayHours: number;
  users: PdfUserSection[];
}

const MARGIN = 32;
const LABEL_WIDTH = 240; // WorkItem / Total column - fixed
const FONT_SIZE = 8;
const CELL_PADDING = 2;
const ICON_SIZE = 9;
// The icon is inline, as on screen: it starts the first line, and the lines below start under it.
const ICON_GAP = 3;
// The colors of the report on screen (App.css): a black id and a blue title.
const ID_COLOR: [number, number, number] = [0, 0, 0];
const TITLE_COLOR: [number, number, number] = [25, 127, 162];

/** A piece of one line of a WorkItem label, in one color. */
export interface LabelRun {
  text: string;
  kind: 'id' | 'title';
}

/**
 * Wraps "id - title" into lines of the given width, at word boundaries, as the browser wraps it on
 * screen. The first line is shorter by `indent`: the icon stands there. A word longer than a line
 * gets a line of its own.
 */
export function labelLines(
  workItem: Pick<WorkItem, 'id' | 'title'>,
  width: number,
  indent: number,
  measure: (text: string) => number,
): LabelRun[][] {
  const words: LabelRun[] = [
    { text: workItem.id, kind: 'id' },
    ...['-', ...workItem.title.split(/\s+/).filter(Boolean)].map((text) => ({ text, kind: 'title' as const })),
  ];
  const lines: LabelRun[][] = [[]];
  let used = 0;
  for (const word of words) {
    const line = lines[lines.length - 1];
    const room = width - (lines.length === 1 ? indent : 0);
    const spaced = line.length ? ` ${word.text}` : word.text;
    if (line.length && used + measure(spaced) > room) {
      lines.push([{ ...word }]);
      used = measure(word.text);
      continue;
    }
    const last = line[line.length - 1];
    if (last?.kind === word.kind) {
      last.text += spaced;
    } else {
      line.push({ text: spaced, kind: word.kind });
    }
    used += measure(spaced);
  }
  return lines;
}

// Loads an icon and flattens it onto a white background via canvas. jsPDF renders PNG alpha
// poorly (visible halo/border artifacts), so we hand it an opaque image instead.
function loadIcon(url: string): Promise<string | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      try {
        // A loaded image always has a size: the browser gives an SVG without one a default.
        const w = img.naturalWidth;
        const h = img.naturalHeight;
        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(null);
          return;
        }
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, w, h);
        ctx.drawImage(img, 0, 0);
        resolve(canvas.toDataURL('image/png'));
      } catch {
        resolve(null);
      }
    };
    img.onerror = () => resolve(null);
    img.src = url;
  });
}

async function loadIcons(urls: string[]): Promise<Map<string, string>> {
  const icons = new Map<string, string>();
  await Promise.all(
    [...new Set(urls)].map(async (url) => {
      const dataUrl = await loadIcon(url);
      if (dataUrl) icons.set(url, dataUrl);
    }),
  );
  return icons;
}

// Builds the timesheet PDF entirely client-side on a standard A3 landscape page, mirroring the
// on-screen report (one section per user with the period total, one block per calendar month
// with dd.MM columns and the work-item type icons). Day columns are sized to fill the page width;
// a month with no data for the user is skipped. This is the export path - the server-side report
// export can't capture the iframe.
export async function exportTimesheetPdf({
  scopeName,
  period,
  dates,
  workingDayHours,
  users,
}: ExportOptions): Promise<void> {
  const iconUrls = users
    .flatMap((u) => uniqueWorkItems(u.records))
    .map((wi) => wi.iconUrl)
    .filter((u): u is string => !!u);
  const icons = await loadIcons(iconUrls);

  const doc = new jsPDF({ orientation: 'landscape', unit: 'pt', format: 'a3' });

  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const months = groupDatesByMonth(dates);
  // Size day columns to fill the page for the longest month - all month blocks use the same width.
  const maxDays = Math.max(1, ...months.map((m) => m.length));
  const dayColWidth = (pageWidth - MARGIN * 2 - LABEL_WIDTH) / maxDays;

  doc.setFont(PDF_FONT, 'bold');
  doc.setFontSize(14);
  doc.text('Timesheet report', MARGIN, 36);
  doc.setFont(PDF_FONT, 'normal');
  doc.setFontSize(9);
  doc.text(`Scope: ${scopeName}    Period: ${period.start} - ${period.end}`, MARGIN, 52);

  let startY = 70;

  const renderRow = (rowDates: Date[], records: WorkRecord[], items: WorkItem[]) => {
    if (startY > pageHeight - 70) {
      doc.addPage();
      startY = 40;
    }

    const weekendColumns = new Set<number>();
    rowDates.forEach((d, i) => {
      if (isWeekend(d)) weekendColumns.add(i + 1); // column 0 is the WorkItem column
    });

    const head = [['WorkItem', ...rowDates.map(formatDayMonth)]];
    const body = items.map((wi) => {
      const key = workItemKey(wi);
      return [`${wi.id} - ${wi.title}`, ...rowDates.map((d) => formatHours(sumHours(records, formatISO(d), key)))];
    });
    const rowTotal = rowDates.reduce((acc, d) => acc + sumHours(records, formatISO(d)), 0);
    const foot = [[`Total: ${rowTotal} h`, ...rowDates.map((d) => formatHours(sumHours(records, formatISO(d))))]];

    // The WorkItem labels are drawn by didDrawCell: autoTable gives a cell one color and no inline icon.
    // It gets the lines only, so that the row takes their height.
    doc.setFontSize(FONT_SIZE);
    const labelWidth = LABEL_WIDTH - 2 * CELL_PADDING;
    const labels = items.map((wi) =>
      labelLines(wi, labelWidth, wi.iconUrl && icons.has(wi.iconUrl) ? ICON_SIZE + ICON_GAP : 0, (text) =>
        doc.getTextWidth(text),
      ),
    );

    autoTable(doc, {
      head,
      body,
      foot,
      startY: startY + 6,
      // A label is drawn whole, so a row must not split across two pages.
      rowPageBreak: 'avoid',
      margin: { left: MARGIN, right: MARGIN },
      tableWidth: LABEL_WIDTH + rowDates.length * dayColWidth,
      styles: {
        font: PDF_FONT,
        fontSize: FONT_SIZE,
        cellPadding: CELL_PADDING,
        halign: 'center',
        cellWidth: dayColWidth,
        lineColor: [0, 0, 0],
        lineWidth: 0.3,
        overflow: 'linebreak',
      },
      headStyles: { fillColor: [207, 207, 207], textColor: 20, halign: 'center' },
      footStyles: { fillColor: [255, 255, 255], textColor: 20, fontStyle: 'bold' },
      columnStyles: {
        0: { cellWidth: LABEL_WIDTH, halign: 'left' },
      },
      didParseCell: (data) => {
        // autoTable applies columnStyles to body cells only, so the head and foot cells of the
        // WorkItem column would keep the global day-column width. The column takes the widest of
        // its cells, so a short period - where a day column is wider than LABEL_WIDTH - blew the
        // WorkItem column up to the day width and pushed the table past the right margin.
        if (data.column.index === 0) {
          data.cell.styles.cellWidth = LABEL_WIDTH;
        }
        if (data.section === 'body' && data.column.index === 0) {
          data.cell.text = labels[data.row.index].map((line) => line.map((run) => run.text).join(''));
        }
        if (data.section !== 'head' && data.column.index > 0 && weekendColumns.has(data.column.index)) {
          data.cell.styles.fillColor = [234, 234, 234];
        }
        // Footer day totals: full-time days bold, part-time days italic (as in the widget).
        if (data.section === 'foot' && data.column.index > 0) {
          const hours = sumHours(records, formatISO(rowDates[data.column.index - 1]));
          data.cell.styles.fontStyle = hours < workingDayHours ? 'italic' : 'bold';
        }
      },
      willDrawCell: (data) => {
        // The label is drawn below, in its colors. autoTable would draw it once more in one color.
        if (data.section === 'body' && data.column.index === 0) {
          data.cell.text = [];
        }
      },
      didDrawCell: (data) => {
        if (data.section === 'body' && data.column.index === 0) {
          // The body rows are the work items, one to one.
          const wi = items[data.row.index];
          const left = data.cell.x + CELL_PADDING;
          const top = data.cell.y + CELL_PADDING;
          const lineHeight = FONT_SIZE * doc.getLineHeightFactor();
          // As autoTable places a first line: its baseline sits this far below the top of the text.
          const baseline = top + FONT_SIZE * (2 - 1.15);
          let indent = 0;
          const src = wi.iconUrl && icons.get(wi.iconUrl);
          if (src) {
            try {
              // Centered on the letters of the first line, as an inline image on screen.
              doc.addImage(src, 'PNG', left, baseline - FONT_SIZE * 0.35 - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE);
              indent = ICON_SIZE + ICON_GAP;
            } catch {
              /* skip if the image can't be drawn */
            }
          }
          labels[data.row.index].forEach((line, i) => {
            let x = left + (i === 0 ? indent : 0);
            const y = baseline + i * lineHeight;
            line.forEach((run) => {
              const width = doc.getTextWidth(run.text);
              doc.setTextColor(...(run.kind === 'id' ? ID_COLOR : TITLE_COLOR));
              doc.text(run.text, x, y);
              if (run.kind === 'id' && wi.resolved) {
                // A resolved work item has its id struck through, as Polarion renders it.
                doc.setDrawColor(...ID_COLOR);
                doc.setLineWidth(0.5);
                doc.line(x, y - FONT_SIZE * 0.3, x + width, y - FONT_SIZE * 0.3);
              }
              x += width;
            });
          });
          // Make the whole WorkItem cell a link to the work item in Polarion.
          doc.link(data.cell.x, data.cell.y, data.cell.width, data.cell.height, {
            url: `${window.location.origin}${workItemUrl(wi)}`,
          });
        }
      },
    });

    startY = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 14;
  };

  users.forEach((user) => {
    if (startY > pageHeight - 110) {
      doc.addPage();
      startY = 40;
    }
    doc.setFont(PDF_FONT, 'bold');
    doc.setFontSize(11);
    doc.text(`${user.name} - total: ${totalHours(user.records)} h`, MARGIN, startY);
    doc.setFont(PDF_FONT, 'normal');
    startY += 8;

    let renderedAny = false;
    months.forEach((monthDates) => {
      // Each block covers only its own month: empty months are skipped (they render as broken empty
      // grids) and a block lists just the work items booked in it. The report on screen does the same.
      const monthRecords = recordsWithin(user.records, monthDates);
      if (monthRecords.length === 0) return;
      renderedAny = true;
      const items = uniqueWorkItems(monthRecords);
      chunk(monthDates, maxDays).forEach((rowDates) => renderRow(rowDates, monthRecords, items));
    });

    if (!renderedAny) {
      doc.setFontSize(9);
      doc.text('- no work records in this period -', MARGIN, startY + 8);
      startY += 18;
    }

    startY += 10;
  });

  doc.save(`timesheet_${period.start}_${period.end}.pdf`);
}
