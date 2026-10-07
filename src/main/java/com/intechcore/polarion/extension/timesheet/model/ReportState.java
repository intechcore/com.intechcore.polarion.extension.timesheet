package com.intechcore.polarion.extension.timesheet.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a report shows on screen: its scope, users and period. A PDF export renders the page on the
 * server, where the report has no browser, so the report reports its selection here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportState {
    private String scopePath; // "/" (root), project id, or a group location path, as scope_path
    private String userIds; // comma separated, as user_ids
    private String startDate;
    private String endDate;
}
