package com.intechcore.polarion.extension.timesheet.widget;

import com.intechcore.polarion.extension.timesheet.model.Project;
import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.User;
import com.intechcore.polarion.extension.timesheet.model.WorkItem;
import com.intechcore.polarion.extension.timesheet.model.WorkRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The static report a PDF export or a print of the widget shows, laid out as the report in the browser:
 * one heading per user and one table per month with records.
 */
class TimesheetReportHtmlTest {

    private static final Project PROJECT = new Project("elibrary", "E-Library");
    private static final User ALICE = new User("alice", "Alice");

    private static WorkRecord record(String date, String workItemId, String html, User user, double hours) {
        return new WorkRecord(date, new WorkItem(PROJECT, workItemId, "Title <" + workItemId + ">", html, null), user, hours);
    }

    private static Timesheet timesheet(WorkRecord... records) {
        return new Timesheet("2026-09-01", "2026-10-31", new ArrayList<>(List.of(records)));
    }

    @Test
    void drawsOneTablePerMonthWithRecordsAndTheTotals() {
        Timesheet timesheet = timesheet(
                record("2026-09-30", "EL-1", "<span class=\"polarion-rp-rendered\">EL-1 - Spec</span>", ALICE, 7.5),
                record("2026-10-03", "EL-1", null, ALICE, 2),
                record("2026-10-05", "EL-2", null, ALICE, 8),
                record("2026-10-05", "EL-9", null, new User("bob", "Bob"), 4));

        String html = new TimesheetReportHtml(List.of(ALICE), LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 5), 8).render(timesheet);

        assertThat(html).contains("<h4>Alice - total: 17.5 h</h4>");
        // September and October, each with only its own days and work items; Bob's hours stay out.
        assertThat(html.split("<table", -1)).hasSize(3);
        assertThat(html).contains(">29.09</th>").contains(">30.09</th>").contains(">01.10</th>").contains(">05.10</th>")
                .doesNotContain(">28.09</th>").doesNotContain("EL-9");
        // Polarion's own rendering goes in as it is; without one, the ID and title are escaped.
        assertThat(html).contains("<span class=\"polarion-rp-rendered\">EL-1 - Spec</span>")
                .contains("<a href=\"/polarion/#/project/elibrary/workitem?id=EL-2\">EL-2 - Title &lt;EL-2&gt;</a>");
        assertThat(html).contains(">7.5 h</td>").contains("Total: 7.5 h").contains("Total: 10 h");
        // A full day is bold, a part-time day italic; the weekend is shaded (3 and 4 October 2026).
        assertThat(html).contains("font-weight:bold;\">8 h</td>").contains("font-style:italic;\">2 h</td>")
                .contains("background:#eaeaea;\">03.10</th>");
    }

    @Test
    void saysWhenAUserHasNoRecordsAndWhenNobodyIsSelected() {
        Timesheet empty = new Timesheet("2026-10-01", "2026-10-31", null);

        assertThat(new TimesheetReportHtml(List.of(new User("x", "<X>")), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 8).render(empty))
                .contains("<h4>&lt;X&gt; - total: 0 h</h4>").contains("- no work records in this period -").doesNotContain("<table");
        assertThat(new TimesheetReportHtml(List.of(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 8).render(empty))
                .contains("No users selected");
        assertThat(TimesheetReportHtml.escape(null)).isEmpty();
        assertThat(TimesheetReportHtml.hours(8.0)).isEqualTo("8");
    }
}
