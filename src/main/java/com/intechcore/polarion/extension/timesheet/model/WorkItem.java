package com.intechcore.polarion.extension.timesheet.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkItem {
    private Project project;
    private String id;
    private String title;
    private String html;    // Polarion's native rendering (icon + linked id + title), set by the manager
    private String iconUrl; // the work item type icon URL (extracted from the native rendering), used by the PDF
}
