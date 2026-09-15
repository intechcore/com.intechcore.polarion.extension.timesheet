import type { WorkRecord } from '../types';
import { groupDatesByMonth } from '../utils/dates';
import { recordsWithin, totalHours, uniqueWorkItems } from '../utils/workRecords';
import TimesheetBlock from './TimesheetBlock';

interface Props {
  title: string;
  records: WorkRecord[];
  dates: Date[];
  workingDayHours: number;
}

// A user's timesheet: a heading with the period total, then one table per calendar month
// (same layout as the PDF export). We care about each user's daily work, not the aggregate.
export default function UserTimesheet({ title, records, dates, workingDayHours }: Props) {
  // Each block covers only its own month: it lists the work items booked in that month, and a month
  // with no records at all is left out. Reporting a year used to repeat every work item of the whole
  // period in all twelve tables, most of them empty. The PDF export drops the same blocks.
  const months = groupDatesByMonth(dates)
    .map((monthDates) => ({ monthDates, monthRecords: recordsWithin(records, monthDates) }))
    .filter(({ monthRecords }) => monthRecords.length > 0);

  return (
    <div className="user-timesheet">
      <h4>
        {title} - total: {totalHours(records)} h
      </h4>
      {months.length === 0 && <p className="timesheet-empty">- no work records in this period -</p>}
      {months.map(({ monthDates, monthRecords }) => (
        <TimesheetBlock
          key={monthDates[0].toISOString()}
          workItems={uniqueWorkItems(monthRecords)}
          records={monthRecords}
          dates={monthDates}
          workingDayHours={workingDayHours}
        />
      ))}
    </div>
  );
}
