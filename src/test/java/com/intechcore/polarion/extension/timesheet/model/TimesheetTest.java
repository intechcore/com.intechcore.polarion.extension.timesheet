package com.intechcore.polarion.extension.timesheet.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimesheetTest {

    private static WorkRecord record(double hours) {
        return new WorkRecord("2026-08-03", new WorkItem(new Project("elibrary", "eLibrary"), "EL-1", "Title", null, null),
                new User("aSeller", "Adam Seller"), hours);
    }

    @Test
    void addWorkRecord_createsTheListOnTheFirstCall() {
        Timesheet timesheet = new Timesheet("2026-08-01", "2026-08-31", null);

        timesheet.addWorkRecord(record(2));

        assertThat(timesheet.getWorkRecords()).hasSize(1);
    }

    @Test
    void addWorkRecord_appendsToAnExistingList() {
        Timesheet timesheet = new Timesheet("2026-08-01", "2026-08-31", new ArrayList<>(List.of(record(1))));

        timesheet.addWorkRecord(record(2));

        assertThat(timesheet.getWorkRecords()).hasSize(2);
    }

    @Test
    void getTotalHours_sumsEveryRecord() {
        Timesheet timesheet = new Timesheet();
        timesheet.setWorkRecords(new ArrayList<>());
        timesheet.addWorkRecord(record(1.5));
        timesheet.addWorkRecord(record(6.5));

        assertThat(timesheet.getTotalHours()).isEqualTo(8d);
    }

    @Test
    void getTotalHours_isZeroWithoutRecords() {
        Timesheet timesheet = new Timesheet("2026-08-01", "2026-08-31", new ArrayList<>());

        assertThat(timesheet.getTotalHours()).isZero();
    }
}
