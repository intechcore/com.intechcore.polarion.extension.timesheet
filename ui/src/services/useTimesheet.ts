import { useEffect, useState } from 'react';
import type { Timesheet } from '../types';
import useRemote from './useRemote';

interface Params {
  userIds: string[];
  startDate: string;
  endDate: string;
  scopePath: string;
}

// Loads the timesheet for the given selection. The previous result is kept until the new one
// arrives (no flicker); `fetching` only drives a subtle indicator.
export default function useTimesheet({ userIds, startDate, endDate, scopePath }: Params) {
  const { sendRequest } = useRemote();
  const [timesheet, setTimesheet] = useState<Timesheet | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fetching, setFetching] = useState(false);

  const userKey = userIds.join(',');

  useEffect(() => {
    if (!userKey || !startDate || !endDate) {
      setTimesheet(null);
      return;
    }
    setFetching(true);
    setError(null);
    let cancelled = false;
    const query = new URLSearchParams({
      user_ids: userKey,
      start_date: startDate,
      end_date: endDate,
      scope_path: scopePath,
    });
    sendRequest({ method: 'GET', url: `/timesheet?${query.toString()}` })
      .then(async (response) => {
        if (!response.ok) {
          const message = await response.json().catch(() => ({}));
          throw new Error(message.message || `Request failed with status ${response.status}`);
        }
        return response.json() as Promise<Timesheet>;
      })
      .then((data) => !cancelled && setTimesheet(data))
      .catch((e: Error) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setFetching(false));
    return () => {
      cancelled = true;
    };
  }, [sendRequest, userKey, startDate, endDate, scopePath]);

  return { timesheet, error, fetching };
}
