import { afterEach, describe, expect, it, vi } from 'vitest';
import { page } from 'vitest/browser';
// Written by TimesheetReportHtmlReferenceTest from what TimesheetReportHtml renders, and kept equal to it.
import printedReport from './fixtures/printed-report.html?raw';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// The report a PDF export of the page writes on the server (TimesheetReportHtml), drawn at the width
// of the page body of the PDF Exporter: landscape A4 and A3 with its margins. The PDF Exporter prints
// it through WeasyPrint, so this is the layout - the shared WorkItem column, the longest month across
// the page, a shorter month as a shorter table - rather than its exact glyphs.

const PAGE_BODY_PX = { a4: 971, a3: 1436 };

let sheet: HTMLElement | undefined;

afterEach(async () => {
  sheet?.remove();
  sheet = undefined;
  // The browser page is shared by every test file: give the next one the viewport it expects.
  await page.viewport(1280, 720);
});

async function printedShot(width: number, name: string) {
  sheet = document.createElement('div');
  sheet.style.cssText = `width:${width}px;padding:0;margin:0;background:#fff;font-family:Arial,sans-serif;font-size:10pt;`;
  sheet.innerHTML = printedReport;
  document.body.appendChild(sheet);
  await vi.waitFor(() => expect([...sheet!.querySelectorAll('img')].every((img) => img.complete)).toBe(true));
  await settleLayout();
  await page.viewport(width + 40, Math.ceil(sheet.scrollHeight) + 40);
  await settleBeforeCapture();
  await expect(page.elementLocator(sheet)).toMatchScreenshot(name);
}

describe.skipIf(!__PIXEL_REFERENCES__)('the report of a PDF export', () => {
  it('on a landscape A4 page', async () => {
    await printedShot(PAGE_BODY_PX.a4, 'printed-a4');
  });

  it('on a landscape A3 page', async () => {
    await printedShot(PAGE_BODY_PX.a3, 'printed-a3');
  });
});
