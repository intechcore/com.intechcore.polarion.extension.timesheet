import { useEffect } from 'react';
import useRemote from './useRemote';

interface Params {
  /** The key the widget gives this report, or null outside a widget. */
  stateKey: string | null;
  scopePath: string;
  userIds: string[];
  startDate: string;
  endDate: string;
}

// The pause after the last change: picking dates or users is a burst of changes, and only where it
// settles matters.
export const REPORT_STATE_DELAY_MS = 300;

// Tells the server what the report shows. A PDF export renders the page on the server, where this
// report has no browser: the widget writes the selection it reads here, instead of its own settings.
export default function useReportState({ stateKey, scopePath, userIds, startDate, endDate }: Params) {
  const { sendRequest } = useRemote();
  const userKey = userIds.join(',');

  useEffect(() => {
    // Nothing the server would accept: no user, or a period the date fields have not finished.
    if (!stateKey || !userKey || !startDate || !endDate || startDate > endDate) {
      return;
    }
    const timer = setTimeout(() => {
      // useRemote answers a network failure with a 503 rather than a rejection, and a selection
      // the server refused only means the export falls back to the widget settings.
      void sendRequest({
        method: 'PUT',
        url: `/report-state/${stateKey}`,
        contentType: 'application/json',
        body: JSON.stringify({ scopePath, userIds: userKey, startDate, endDate }),
      });
    }, REPORT_STATE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [sendRequest, stateKey, scopePath, userKey, startDate, endDate]);
}
