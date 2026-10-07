package com.intechcore.polarion.extension.timesheet.widget;

import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.User;
import com.intechcore.polarion.extension.timesheet.model.WorkItem;
import com.intechcore.polarion.extension.timesheet.model.WorkRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The report of the widget as static HTML, for a PDF export or a print of the page: an iframe has no
 * content there. Per user a heading with the total, then one table per calendar month that has
 * records, as the report shows them (ui/src/components/UserTimesheet.tsx and TimesheetBlock.tsx).
 * The styles are inline: the export carries no style sheet of this extension.
 */
public class TimesheetReportHtml {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM");
    private static final String CELL = "border:1px solid #000000;padding:2px 3px;white-space:nowrap;";
    private static final String WEEKEND = "background:#eaeaea;";
    private static final Set<DayOfWeek> WEEKEND_DAYS = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final List<User> users;
    private final LocalDate start;
    private final LocalDate end;
    private final int workingDayHours;

    /**
     * @param users the users of the report, in their order, with the names to show
     */
    public TimesheetReportHtml(@NotNull List<User> users, @NotNull LocalDate start, @NotNull LocalDate end, int workingDayHours) {
        this.users = users;
        this.start = start;
        this.end = end;
        this.workingDayHours = workingDayHours;
    }

    public @NotNull String render(@NotNull Timesheet timesheet) {
        List<WorkRecord> records = timesheet.getWorkRecords() == null ? List.of() : timesheet.getWorkRecords();
        StringBuilder html = new StringBuilder("<div class=\"timesheet-report\">");
        if (users.isEmpty()) {
            return html.append("<p>No users selected</p></div>").toString();
        }
        for (User user : users) {
            List<WorkRecord> own = records.stream().filter(r -> r.getUser() != null && user.getId().equals(r.getUser().getId())).toList();
            html.append("<h4>").append(escape(user.getName())).append(" - total: ").append(hours(sum(own))).append(" h</h4>");
            List<List<LocalDate>> months = months().stream().filter(days -> !within(own, days).isEmpty()).toList();
            if (months.isEmpty()) {
                html.append("<p style=\"color:#6b6b6b;\">- no work records in this period -</p>");
            }
            for (List<LocalDate> days : months) {
                block(html, within(own, days), days);
            }
        }
        return html.append("</div>").toString();
    }

    private void block(StringBuilder html, List<WorkRecord> records, List<LocalDate> days) {
        html.append("<table style=\"border-collapse:collapse;font-size:7pt;margin-bottom:8px;\"><thead><tr style=\"background:#cfcfcf;\">")
                .append("<th style=\"").append(CELL).append("text-align:left;\">WorkItem</th>");
        for (LocalDate day : days) {
            html.append("<th style=\"").append(CELL).append(weekend(day)).append("\">").append(day.format(DAY_MONTH)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        Map<String, WorkItem> workItems = new LinkedHashMap<>();
        records.forEach(r -> workItems.putIfAbsent(key(r.getWorkItem()), r.getWorkItem()));
        for (Map.Entry<String, WorkItem> workItem : workItems.entrySet()) {
            html.append("<tr><td style=\"").append(CELL).append("white-space:normal;\">").append(label(workItem.getValue())).append("</td>");
            for (LocalDate day : days) {
                double hours = sum(on(records, day, workItem.getKey()));
                html.append("<td style=\"").append(CELL).append(weekend(day)).append("\">").append(hours > 0 ? hours(hours) + " h" : "").append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody><tfoot><tr><td style=\"").append(CELL).append("\">Total: ").append(hours(sum(records))).append(" h</td>");
        for (LocalDate day : days) {
            double hours = sum(on(records, day, null));
            String weight = hours < workingDayHours ? "font-style:italic;" : "font-weight:bold;";
            html.append("<td style=\"").append(CELL).append(weekend(day)).append(weight).append("\">")
                    .append(hours > 0 ? hours(hours) + " h" : "").append("</td>");
        }
        html.append("</tr></tfoot></table>");
    }

    /** The days of the period, one list per calendar month. */
    private List<List<LocalDate>> months() {
        Map<YearMonth, List<LocalDate>> months = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            months.computeIfAbsent(YearMonth.from(day), month -> new ArrayList<>()).add(day);
        }
        return List.copyOf(months.values());
    }

    private static List<WorkRecord> within(List<WorkRecord> records, List<LocalDate> days) {
        List<String> isoDays = days.stream().map(LocalDate::toString).toList();
        return records.stream().filter(r -> isoDays.contains(r.getDate())).toList();
    }

    private static List<WorkRecord> on(List<WorkRecord> records, LocalDate day, @Nullable String workItemKey) {
        String iso = day.toString();
        return records.stream()
                .filter(r -> iso.equals(r.getDate()) && (workItemKey == null || workItemKey.equals(key(r.getWorkItem()))))
                .toList();
    }

    private static double sum(List<WorkRecord> records) {
        return records.stream().mapToDouble(WorkRecord::getHours).sum();
    }

    private static String key(WorkItem workItem) {
        return (workItem.getProject() == null ? "" : workItem.getProject().getId()) + "/" + workItem.getId();
    }

    /**
     * Polarion's own rendering of the work item, as the report shows it, or the ID and title as a link
     * when there is none. The rendering comes from Polarion, not from a user, so it goes in as it is.
     */
    private static String label(WorkItem workItem) {
        if (workItem.getHtml() != null) {
            return workItem.getHtml();
        }
        String project = workItem.getProject() == null ? "" : workItem.getProject().getId();
        String href = "/polarion/#/project/" + project + "/workitem?id=" + workItem.getId();
        return "<a href=\"" + escape(href) + "\">" + escape(workItem.getId()) + " - " + escape(workItem.getTitle()) + "</a>";
    }

    private static String weekend(LocalDate day) {
        return WEEKEND_DAYS.contains(day.getDayOfWeek()) ? WEEKEND : "";
    }

    /** Hours as the report writes them: 8, 7.5, never 8.0. */
    static String hours(double hours) {
        return BigDecimal.valueOf(hours).stripTrailingZeros().toPlainString();
    }

    static @NotNull String escape(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
