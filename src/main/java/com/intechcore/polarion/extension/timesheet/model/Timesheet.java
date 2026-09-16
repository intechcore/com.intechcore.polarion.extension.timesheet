package com.intechcore.polarion.extension.timesheet.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Timesheet {
    private String startDate;
    private String finishDate;
    private List<WorkRecord> workRecords;

    public void addWorkRecord(@NotNull WorkRecord workRecord) {
        if (workRecords == null) {
            workRecords = new ArrayList<>();
        }
        workRecords.add(workRecord);
    }

    public double getTotalHours() {
        return workRecords.stream()
                .map(WorkRecord::getHours)
                .reduce(0d, Double::sum);
    }
}
