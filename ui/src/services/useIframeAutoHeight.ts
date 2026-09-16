import { useLayoutEffect } from 'react';

// Posts the content height to the embedding widget iframe so it can resize itself.
// A ResizeObserver re-measures after async data loads and after a horizontal scrollbar
// appears, so the last table's bottom border is never clipped.
//
// Only document.body.scrollHeight is used (the content height). documentElement.scrollHeight
// must NOT be used: it tracks the iframe viewport, so combined with the widget's height
// adjustment it would create a feedback loop that grows the page indefinitely.

// The pickers render their popup into a `position: fixed` portal on <body>, which neither counts
// toward body.scrollHeight nor changes body's size when it opens - so the iframe kept the height it
// had and cut the option list off. Their bottom edge is measured separately. Only the popups are
// measured, not every body child: a full-viewport fixed overlay (the toast host) would report a
// bottom that grows with the iframe, which is exactly the runaway loop noted above.
// The portal itself is a zero-height anchor box pinned to the trigger; the option list hangs off it
// absolutely, so the list is what has to be measured.
const POPUP_SELECTOR = '.sd-portal .options';

function popupsBottom(): number {
  return (
    [...document.querySelectorAll<HTMLElement>(POPUP_SELECTOR)]
      // A closed portal stays in the DOM at display:none, and reports no client rect.
      .filter((el) => el.getClientRects().length > 0)
      .reduce((bottom, el) => Math.max(bottom, el.getBoundingClientRect().bottom + window.scrollY), 0)
  );
}

export default function useIframeAutoHeight() {
  useLayoutEffect(() => {
    const postHeight = () => {
      const height = Math.max(document.body.scrollHeight, Math.ceil(popupsBottom()));
      window.parent?.postMessage({ type: 'timesheet-app-height', height }, '*');
    };
    postHeight();

    const observer = new ResizeObserver(postHeight);
    observer.observe(document.body);

    // Opening, filtering and closing a popup all resize its portal (a closed one measures 0x0), so
    // one observer covers every case. The portals are created when a picker mounts, hence the watch
    // on <body> for new ones.
    const popupObserver = new ResizeObserver(postHeight);
    const observePopups = () => {
      popupObserver.disconnect();
      document.querySelectorAll(POPUP_SELECTOR).forEach((el) => popupObserver.observe(el));
    };
    observePopups();
    const bodyChildren = new MutationObserver(observePopups);
    bodyChildren.observe(document.body, { childList: true });

    window.addEventListener('resize', postHeight);
    return () => {
      observer.disconnect();
      popupObserver.disconnect();
      bodyChildren.disconnect();
      window.removeEventListener('resize', postHeight);
    };
  }, []);
}
