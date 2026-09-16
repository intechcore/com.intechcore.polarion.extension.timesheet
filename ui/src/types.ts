export interface Project {
  id: string;
  name: string;
}

export interface User {
  id: string;
  name: string;
}

export interface WorkItem {
  project: Project;
  id: string;
  title: string;
  html?: string; // Polarion's native rendering (icon + linked id + title)
  iconUrl?: string; // work item type icon URL (used by the PDF export)
}

export interface WorkRecord {
  date: string; // yyyy-MM-dd
  workItem: WorkItem;
  user: User;
  hours: number;
}

export interface Timesheet {
  startDate: string;
  finishDate: string;
  workRecords: WorkRecord[];
}

export interface ScopeInfo {
  path: string; // "/" (root), a project id, or a group location path
  name: string;
  type: 'root' | 'group' | 'project';
  depth: number;
}
