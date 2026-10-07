package com.intechcore.polarion.extension.timesheet.widget;

import com.intechcore.polarion.extension.timesheet.model.Project;
import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.User;
import com.intechcore.polarion.extension.timesheet.model.WorkItem;
import com.intechcore.polarion.extension.timesheet.model.WorkRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps ui/test/fixtures/printed-report.html equal to what {@link TimesheetReportHtml} writes. The
 * browser suite draws that file at the width of a PDF page and compares it with its pixel reference
 * (ui/test/PrintedReport.visual.test.tsx), so the report of a PDF export has a picture too.
 *
 * <p>A change of the report fails this test until the file is written again:
 * {@code mvn test -Dtest=TimesheetReportHtmlReferenceTest -Dupdate.printed=true}, then
 * {@code npm run test:update:docker} in ui/ for the pictures.
 */
class TimesheetReportHtmlReferenceTest {

    static final Path REFERENCE = Path.of("ui/test/fixtures/printed-report.html");

    private static final Project PROJECT = new Project("elibrary", "E-Library");
    private static final User ANNA = new User("aSeller", "Anna Seller");
    private static final User BORIS = new User("bTest", "Boris Test");
    private static final User CLARA = new User("cUser", "Clara User");

    // Polarion's own rendering of a title, with the type icon the tests serve a stand-in for.
    private static final String ICON = "<img src=\"/polarion/ria/images/enums/type_task.svg\" class=\"polarion-Icons\"/>";

    private static WorkItem item(String id, String title) {
        String rendered = "<span class=\"polarion-no-style-cleanup\"><a class=\"polarion-Hyperlink\" href=\"#\">"
                + ICON + "<span>" + id + "</span><span> - " + title + "</span></a></span>";
        return new WorkItem(PROJECT, id, title, rendered, null);
    }

    /** June, July and August 2026: 30 and 31 days, weekends, full and part days, a user without records. */
    static String report() {
        WorkItem refactor = item("EL-14463", "Refactor the loan renewal workflow");
        WorkItem review = item("EL-14460", "Review the REST API documentation and the examples of every endpoint it lists");
        WorkItem plain = new WorkItem(PROJECT, "EL-7", "Abbreviations and Terms", null, null);
        List<WorkRecord> records = new ArrayList<>(List.of(
                new WorkRecord("2026-06-01", refactor, ANNA, 8),
                new WorkRecord("2026-06-02", review, ANNA, 4),
                new WorkRecord("2026-06-02", plain, ANNA, 4),
                new WorkRecord("2026-06-30", refactor, ANNA, 2),
                new WorkRecord("2026-08-03", review, ANNA, 6),
                new WorkRecord("2026-08-31", refactor, ANNA, 8),
                new WorkRecord("2026-07-15", plain, BORIS, 3)));
        Timesheet timesheet = new Timesheet("2026-06-01", "2026-08-31", records);
        return new TimesheetReportHtml("E-Library", List.of(ANNA, BORIS, CLARA),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), 8).render(timesheet);
    }

    @Test
    void keepsTheReferenceOfTheBrowserSuiteUpToDate() throws IOException {
        String html = report() + "\n";
        if (Boolean.getBoolean("update.printed")) {
            Files.writeString(REFERENCE, html, StandardCharsets.UTF_8);
        }

        assertThat(REFERENCE).exists();
        assertThat(Files.readString(REFERENCE, StandardCharsets.UTF_8))
                .as("%s is out of date: write it with -Dupdate.printed=true", REFERENCE)
                .isEqualTo(html);
    }
}
