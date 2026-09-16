import { useEffect, useState } from 'react';
import type { ScopeInfo, User } from '../types';
import useRemote from './useRemote';

// Loads the report's option lists (users, scopes) and the current user id for defaulting.
export default function useReportOptions() {
  const { sendRequest } = useRemote();
  const [users, setUsers] = useState<User[]>([]);
  const [scopes, setScopes] = useState<ScopeInfo[]>([]);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);

  useEffect(() => {
    sendRequest({ method: 'GET', url: '/users' })
      .then((r) => (r.ok ? (r.json() as Promise<User[]>) : []))
      .then(setUsers)
      .catch(() => {});
    sendRequest({ method: 'GET', url: '/scopes' })
      .then((r) => (r.ok ? (r.json() as Promise<ScopeInfo[]>) : []))
      .then(setScopes)
      .catch(() => {});
    sendRequest({ method: 'GET', url: '/current-user' })
      .then((r) => (r.ok && r.status !== 204 ? (r.json() as Promise<User>) : null))
      .then((u) => u && setCurrentUserId(u.id))
      .catch(() => {});
  }, [sendRequest]);

  return { users, scopes, currentUserId };
}
