import { afterEach, describe, expect, it, vi } from 'vitest';
import { page } from 'vitest/browser';
// The style sheets of the PDF Exporter, as it ships them; it joins the two for a Live Report export.
import dlePdfExportCss from './fixtures/pdf-exporter/dle-pdf-export.css?raw';
import wikiCss from './fixtures/pdf-exporter/wiki.css?raw';
// Written by TimesheetReportHtmlReferenceTest from what TimesheetReportHtml renders, and kept equal to it.
import printedReport from './fixtures/printed-report.html?raw';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// The report a PDF export of the page writes on the server (TimesheetReportHtml), under the style
// sheets of the PDF Exporter, at the width of its page body: a landscape page less the 80px and 60px
// side margins of its @page rule. The PDF Exporter prints through WeasyPrint, so this pins the layout
// and the styles rather than the exact glyphs.
const PX_PER_MM = 96 / 25.4;
const pageBody = (widthMm: number) => Math.round(widthMm * PX_PER_MM - 80 - 60);

let added: HTMLElement[] = [];

afterEach(async () => {
  added.forEach((element) => element.remove());
  added = [];
  // The browser page is shared by every test file: give the next one the viewport it expects.
  await page.viewport(1280, 720);
});

async function printedShot(widthMm: number, name: string) {
  const style = document.createElement('style');
  style.textContent = `${dlePdfExportCss}\n${wikiCss}`;
  const sheet = document.createElement('div');
  sheet.style.cssText = `width:${pageBody(widthMm)}px;background:#fff;`;
  sheet.innerHTML = printedReport;
  added = [style, sheet];
  document.head.appendChild(style);
  document.body.appendChild(sheet);

  await vi.waitFor(() => expect([...sheet.querySelectorAll('img')].every((img) => img.complete)).toBe(true));
  await settleLayout();
  await page.viewport(pageBody(widthMm) + 40, Math.ceil(sheet.scrollHeight) + 40);
  await settleBeforeCapture();
  await expect(page.elementLocator(sheet)).toMatchScreenshot(name);
}

describe.skipIf(!__PIXEL_REFERENCES__)('the report of a PDF export', () => {
  it('on a landscape A4 page', async () => {
    await printedShot(297, 'printed-a4');
  });

  it('on a landscape A3 page', async () => {
    await printedShot(420, 'printed-a3');
  });
});
