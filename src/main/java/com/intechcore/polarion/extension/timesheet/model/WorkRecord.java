package com.intechcore.polarion.extension.timesheet.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkRecord {
    private String date;
    private WorkItem workItem;
    private User user;
    private double hours;
}
