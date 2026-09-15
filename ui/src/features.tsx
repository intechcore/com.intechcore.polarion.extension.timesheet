import type { ComponentType } from 'react';
import ReportView from './components/ReportView';
import About from './pages/About';

/**
 * A single navigable page of the app. The `id` is what appears in the URL as `?feature=<id>`: the
 * administration entry in `hivemodule.xml` opens `about`, and the Live Report widget embeds `report`
 * (see TimesheetReportWidgetRenderer). Keep the ids stable - they live outside this app.
 */
export interface Feature {
  id: string;
  label: string;
  description: string;
  component: ComponentType;
}

export const FEATURES: Feature[] = [
  {
    id: 'report',
    label: 'Timesheet report',
    description: 'Work records per user over a period, as embedded in a Live Report.',
    component: ReportView,
  },
  {
    id: 'about',
    label: 'About',
    description: 'Extension version and general information.',
    component: About,
  },
];

export function findFeature(id: string | null): Feature | undefined {
  return FEATURES.find((f) => f.id === id);
}
