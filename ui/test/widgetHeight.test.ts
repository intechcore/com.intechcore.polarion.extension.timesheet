import { afterEach, describe, expect, inject, it } from 'vitest';

// The height listener the Live Report widget writes onto the page (src/main/resources/js/
// widget-height.js, served by TimesheetReportWidgetRenderer). The Java side can assert the text of
// that script and never what it does, so its behavior is driven here, in a real browser: a message
// from the widget's own frame resizes it, and everything else leaves it alone.
const source = inject('widgetHeightScript');

const FRAME_ID = 'timesheet-report-under-test';

/** Loads the script and binds the listener to a frame id, exactly as the widget does. */
function bindListener(frameId: string): void {
  const load = new Function(`${source}\nreturn timesheetSyncIframeHeight;`) as () => (id: string) => void;
  load()(frameId);
}

function addFrame(id?: string): HTMLIFrameElement {
  const frame = document.createElement('iframe');
  if (id) {
    frame.id = id;
  }
  document.body.appendChild(frame);
  return frame;
}

/**
 * Posts from inside an iframe, so the browser sets `event.source` to that frame. Calling
 * postMessage from the test would name the top window instead, which is the case under test.
 *
 * The returned promise settles once the message has been delivered to this page. Listeners run in
 * the order they were added and this one is added after the script's, so by then the listener has
 * had its turn and the frame either changed or did not.
 */
function postFromFrame(frame: HTMLIFrameElement, height: string): Promise<void> {
  const delivered = new Promise<void>((resolve) => {
    window.addEventListener('message', () => resolve(), { once: true });
  });

  const doc = frame.contentDocument!;
  const script = doc.createElement('script');
  script.textContent = `parent.postMessage({ type: 'timesheet-app-height', height: ${height} }, '*')`;
  doc.body.appendChild(script);

  return delivered;
}

describe('the widget height listener', () => {
  afterEach(() => {
    document.querySelectorAll('iframe').forEach((element) => element.remove());
  });

  it('resizes its own frame, with the two pixels of the last border', async () => {
    const frame = addFrame(FRAME_ID);
    bindListener(FRAME_ID);

    await postFromFrame(frame, '640');

    expect(frame.style.height).toBe('642px');
  });

  it('ignores a message from another window', async () => {
    const frame = addFrame(FRAME_ID);
    const foreign = addFrame();
    bindListener(FRAME_ID);

    await postFromFrame(foreign, '4000');

    expect(frame.style.height).toBe('');
  });

  it('ignores a message of another type', async () => {
    const frame = addFrame(FRAME_ID);
    bindListener(FRAME_ID);

    const delivered = new Promise<void>((resolve) => {
      window.addEventListener('message', () => resolve(), { once: true });
    });
    const doc = frame.contentDocument!;
    const script = doc.createElement('script');
    script.textContent = `parent.postMessage({ type: 'some-other-height', height: 4000 }, '*')`;
    doc.body.appendChild(script);
    await delivered;

    expect(frame.style.height).toBe('');
  });

  // JSON carries neither Infinity nor NaN, so each case is a literal the frame evaluates.
  it.each([
    ['a string', "'4000'"],
    ['null', 'null'],
    ['undefined', 'undefined'],
    ['an infinity', 'Infinity'],
    ['a not-a-number', 'NaN'],
  ])('ignores a height which is %s', async (_case, height) => {
    const frame = addFrame(FRAME_ID);
    bindListener(FRAME_ID);

    await postFromFrame(frame, height);

    expect(frame.style.height).toBe('');
  });

  it('does nothing when the frame it was bound to is not on the page', async () => {
    const frame = addFrame(FRAME_ID);
    bindListener('timesheet-report-absent');

    await postFromFrame(frame, '500');

    expect(frame.style.height).toBe('');
  });
});
