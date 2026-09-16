import { useEffect, useMemo, useState } from 'react';
import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { SelectOption } from '@sbb-polarion/react-sbb-polarion';
import useIframeAutoHeight from '../services/useIframeAutoHeight';
import useReportOptions from '../services/useReportOptions';
import useTimesheet from '../services/useTimesheet';
import type { ScopeInfo } from '../types';
import { currentMonthRange, datesInPeriod, parseDate } from '../utils/dates';
import { groupByUser } from '../utils/workRecords';
import DateRangePicker from './DateRangePicker';
import ExportPdfButton from './ExportPdfButton';
import UserTimesheet from './UserTimesheet';

const DEFAULT_WORKING_DAY_HOURS = 8;

const SCOPE_ICON: Record<ScopeInfo['type'], string> = {
  root: '/polarion/ria/images/repository_persp.gif',
  group: '/polarion/ria/images/projectlist/project_group.gif',
  project: '/polarion/ria/images/projectlist/project.gif',
};

export default function ReportView() {
  // The Live Report widget presets the defaults (scope, users, full-time threshold) via query params.
  const seed = useMemo(() => {
    const q = new URLSearchParams(window.location.search);
    const wdh = parseInt(q.get('workingDayInHours') ?? '', 10);
    return {
      scopePath: q.get('scope') || '/',
      userIds: (q.get('userIds') ?? '')
        .split(',')
        .map((u) => u.trim())
        .filter(Boolean),
      workingDayHours: !isNaN(wdh) && wdh > 0 ? wdh : DEFAULT_WORKING_DAY_HOURS,
    };
  }, []);
  const month = useMemo(currentMonthRange, []);

  const { users, scopes, currentUserId } = useReportOptions();
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>(seed.userIds);
  const [scopePath, setScopePath] = useState(seed.scopePath);
  const [startDate, setStartDate] = useState(month.start);
  const [endDate, setEndDate] = useState(month.end);

  // Default the selection to the current user once it is known.
  useEffect(() => {
    if (currentUserId) {
      setSelectedUserIds((prev) => (prev.length ? prev : [currentUserId]));
    }
  }, [currentUserId]);

  const { timesheet, error, fetching } = useTimesheet({ userIds: selectedUserIds, startDate, endDate, scopePath });

  const dates = useMemo(() => {
    const from = parseDate(startDate);
    const to = parseDate(endDate);
    return startDate && endDate && from <= to ? datesInPeriod(from, to) : [];
  }, [startDate, endDate]);

  const recordsByUser = useMemo(() => groupByUser(timesheet?.workRecords ?? []), [timesheet]);
  useIframeAutoHeight();

  const userName = (id: string): string => users.find((u) => u.id === id)?.name ?? id;

  // Polarion's classic colored scope icons, matching the standard project navigation (the topicIcons
  // set is white-on-transparent and invisible on a light background).
  const scopeOptions: SelectOption[] = scopes.map((scope) => ({
    id: scope.path,
    name: scope.name,
    iconURL: SCOPE_ICON[scope.type],
    indent: scope.depth > 0,
  }));
  const userOptions: SelectOption[] = users.map((user) => ({ id: user.id, name: `${user.name} (${user.id})` }));

  const scopeName = scopes.find((s) => s.path === scopePath)?.name ?? scopePath;
  const pdfUsers = selectedUserIds.map((id) => ({ name: userName(id), records: recordsByUser.get(id) ?? [] }));

  return (
    <div className="timesheet-report">
      <h3>Timesheet report</h3>

      <div className="timesheet-controls">
        <div className="control control-scope">
          <span>Scope</span>
          <SearchableSelect options={scopeOptions} value={scopePath} onChange={setScopePath} />
        </div>
        <div className="control control-users">
          <span>Users</span>
          <SearchableSelect
            multiple
            options={userOptions}
            value={selectedUserIds}
            onChange={setSelectedUserIds}
            placeholder="Add / search users…"
          />
        </div>
        <DateRangePicker
          startDate={startDate}
          endDate={endDate}
          onStartChange={setStartDate}
          onEndChange={setEndDate}
        />
        <div className="control">
          <span>&nbsp;</span>
          <ExportPdfButton
            scopeName={scopeName}
            period={{ start: startDate, end: endDate }}
            dates={dates}
            workingDayHours={seed.workingDayHours}
            users={pdfUsers}
            disabled={selectedUserIds.length === 0 || dates.length === 0 || fetching}
          />
        </div>
      </div>

      {error && <p className="timesheet-error">{error}</p>}
      {selectedUserIds.length === 0 && <p>No users selected</p>}
      {fetching && <p className="timesheet-status">Updating…</p>}

      {selectedUserIds.map((id) => (
        <UserTimesheet
          key={id}
          title={userName(id)}
          records={recordsByUser.get(id) ?? []}
          dates={dates}
          workingDayHours={seed.workingDayHours}
        />
      ))}
    </div>
  );
}
