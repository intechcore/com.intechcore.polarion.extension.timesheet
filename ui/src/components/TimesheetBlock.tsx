import useScrollEdges from '../services/useScrollEdges';
import type { WorkItem, WorkRecord } from '../types';
import { formatDayMonth, formatISO, isWeekend } from '../utils/dates';
import { sanitizeWorkItemHtml } from '../utils/html';
import { formatHours, sumHours, workItemKey, workItemUrl } from '../utils/workRecords';

interface Props {
  workItems: WorkItem[];
  records: WorkRecord[];
  dates: Date[];
  workingDayHours: number;
}

// One month block of a user's timesheet (kept in sync with the PDF block layout).
export default function TimesheetBlock({ workItems, records, dates, workingDayHours }: Props) {
  const blockTotal = dates.reduce((acc, d) => acc + sumHours(records, formatISO(d)), 0);
  const { ref, barRef, edges, bar } = useScrollEdges<HTMLDivElement, HTMLDivElement>();
  // The frame shades the side where days are hidden, so a cut-off column reads as scrollable.
  const frameClass = ['timesheet-table-frame', edges.left && 'scrolled-left', edges.right && 'more-right']
    .filter(Boolean)
    .join(' ');

  return (
    <div className="timesheet-block">
      <div className={frameClass}>
        <div className="timesheet-table-wrap" ref={ref}>
          <table className="timesheet">
            <thead>
              <tr>
                <th>WorkItem</th>
                {dates.map((date) => (
                  <th key={formatISO(date)} className={isWeekend(date) ? 'weekend' : undefined}>
                    {formatDayMonth(date)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {workItems.map((wi) => {
                const key = workItemKey(wi);
                return (
                  <tr key={key}>
                    <td>
                      {wi.html ? (
                        <span dangerouslySetInnerHTML={{ __html: sanitizeWorkItemHtml(wi.html) }} />
                      ) : (
                        <a href={workItemUrl(wi)} target="_blank" rel="noreferrer">
                          {wi.id} - {wi.title}
                        </a>
                      )}
                    </td>
                    {dates.map((date) => {
                      const iso = formatISO(date);
                      return (
                        <td key={iso} className={isWeekend(date) ? 'weekend' : undefined}>
                          {formatHours(sumHours(records, iso, key))}
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
            <tfoot>
              <tr>
                <td>Total: {blockTotal} h</td>
                {dates.map((date) => {
                  const iso = formatISO(date);
                  const hours = sumHours(records, iso);
                  const className = [
                    isWeekend(date) ? 'weekend' : '',
                    hours < workingDayHours ? 'part-time' : 'full-time',
                  ]
                    .filter(Boolean)
                    .join(' ');
                  return (
                    <td key={iso} className={className}>
                      {formatHours(hours)}
                    </td>
                  );
                })}
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
      {/* The days' own scrollbar, synced with the table. It repeats what a trackpad does on the table,
        so it stays out of the accessibility tree. */}
      <div
        className="timesheet-scrollbar"
        ref={barRef}
        hidden={!bar}
        aria-hidden="true"
        style={bar ? { marginLeft: bar.offset } : undefined}
      >
        <div style={bar ? { width: bar.contentWidth } : undefined} />
      </div>
    </div>
  );
}
