package com.intechcore.polarion.extension.timesheet.manager;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.timesheet.model.*;
import com.polarion.alm.projects.model.IGroupEntity;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.shared.api.Scope;
import com.polarion.alm.shared.api.model.wi.WorkItemReference;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkRecord;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.service.repository.IRepositoryService;
import com.polarion.subterra.base.location.ILocation;
import com.polarion.subterra.base.location.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TimesheetReportManager {

    private final PolarionService polarionService;

    public TimesheetReportManager() {
        this.polarionService = new PolarionService();
    }

    public TimesheetReportManager(@NotNull PolarionService polarionService) {
        this.polarionService = polarionService;
    }

    @SuppressWarnings("unchecked")
    public Timesheet getTimesheet(@NotNull Scope scope, @NotNull List<String> userIds, @NotNull String startDate, @NotNull String endDate) {
        String query = createQuery(scope, userIds, startDate, endDate);

        List<IWorkItem> workItems = polarionService.getTrackerService().getDataService().searchInstances(IWorkItem.PROTO, query, null);

        Timesheet timesheet = new Timesheet(startDate, endDate, new ArrayList<>(workItems.size()));

        for (IWorkItem workItem : workItems) {
            IPObjectList<IWorkRecord> workRecords = workItem.getWorkRecords();
            for (IWorkRecord workRecord : workRecords) {
                String recordDate = workRecord.getDate().toString();
                // The query selects work items that have a record in the period, but each item may
                // also carry records outside it - keep only the ones for the selected users AND dates.
                if (userIds.contains(workRecord.getUser().getId()) && isWithinPeriod(recordDate, startDate, endDate)) {
                    timesheet.addWorkRecord(
                            new WorkRecord(
                                    recordDate,
                                    new WorkItem(
                                            new Project(workRecord.getProjectId(), workRecord.getProject().getName()),
                                            workRecord.getWorkItem().getId(),
                                            workRecord.getWorkItem().getTitle(),
                                            null,
                                            null
                                    ),
                                    new User(workRecord.getUser().getId(), workRecord.getUser().getName()),
                                    workRecord.getTimeSpent().getHours()
                            )
                    );
                    workRecord.forget();
                }
            }
        }

        renderWorkItemsHtml(timesheet);

        return timesheet;
    }

    // Inclusive date-range check tolerant of the date format ("yyyy-MM-dd" or "yyyyMMdd").
    boolean isWithinPeriod(@NotNull String date, @NotNull String start, @NotNull String end) {
        String d = digits(date);
        String s = digits(start);
        String e = digits(end);
        return d.compareTo(s) >= 0 && d.compareTo(e) <= 0;
    }

    private static @NotNull String digits(@NotNull String value) {
        StringBuilder digits = new StringBuilder(value.length());
        value.chars().filter(Character::isDigit).forEach(c -> digits.append((char) c));
        return digits.toString();
    }

    // Fills each work item with Polarion's native rendering (icon + linked id + title), as shown
    // by the standard report widgets. Rendered once per unique work item inside one read-only
    // transaction.
    // Package-private, not private: getTimesheet always passes a filled list, so the empty cases
    // are reachable only from a test. Timesheet itself allows a null list, hence the guard.
    void renderWorkItemsHtml(@NotNull Timesheet timesheet) {
        List<WorkRecord> workRecords = timesheet.getWorkRecords();
        if (workRecords == null || workRecords.isEmpty()) {
            return;
        }
        TransactionalExecutor.executeSafelyInReadOnlyTransaction(transaction -> {
            Map<String, String> htmlByKey = new HashMap<>();
            for (WorkRecord workRecord : workRecords) {
                WorkItem workItem = workRecord.getWorkItem();
                String key = workItem.getProject().getId() + "/" + workItem.getId();
                String html = htmlByKey.computeIfAbsent(key, k -> renderWorkItemHtml(transaction, workItem));
                workItem.setHtml(html);
                workItem.setIconUrl(extractIconUrl(html));
            }
            return null;
        });
    }

    private static final String IMG = "<img";
    private static final String SRC = "src=\"";

    /** The src of the first image Polarion rendered with a src: the icon of the work item type. */
    @Nullable String extractIconUrl(@Nullable String html) {
        if (html == null) {
            return null;
        }
        for (int img = html.indexOf(IMG); img >= 0; img = html.indexOf(IMG, img + IMG.length())) {
            int end = html.indexOf('>', img);
            String tag = end < 0 ? html.substring(img) : html.substring(img, end);
            String src = attribute(tag);
            if (src != null) {
                return src;
            }
        }
        return null;
    }

    /** The src of one tag. It must start an attribute, so a data-src does not count. */
    private static @Nullable String attribute(@NotNull String tag) {
        for (int at = tag.indexOf(SRC); at >= 0; at = tag.indexOf(SRC, at + 1)) {
            int start = at + SRC.length();
            int close = tag.indexOf('"', start);
            if (Character.isWhitespace(tag.charAt(at - 1)) && close > start) {
                return tag.substring(start, close);
            }
        }
        return null;
    }

    private @Nullable String renderWorkItemHtml(@NotNull com.polarion.alm.shared.api.transaction.ReadOnlyTransaction transaction, @NotNull WorkItem workItem) {
        try {
            return new WorkItemReference(workItem.getProject().getId(), workItem.getId())
                    .get(transaction)
                    .render()
                    .withLinks()
                    .withTitle()
                    .htmlFor()
                    .rpeView();
        } catch (Exception e) {
            return null;
        }
    }

    private @NotNull String createQuery(@NotNull Scope scope, @NotNull List<String> userIds, @NotNull String startDate, @NotNull String endDate) {
        @NotNull String query = createWorkRecordsQuery(startDate, endDate);
        @Nullable String queryUserWorkRecords = createUserWorkRecordsQuery(userIds);
        if (queryUserWorkRecords != null) {
            query = query + " AND (" + queryUserWorkRecords + ")";
        }
        @Nullable String scopeQuery = createScopeQuery(scope);
        if (scopeQuery != null) {
            query = query + " AND (" + scopeQuery + ")";
        }
        return query;
    }

    /** Whether Polarion knows the user. An id it does not know is answered with an unresolvable object. */
    public boolean userExists(@NotNull String userId) {
        try {
            IUser user = polarionService.getProjectService().getUser(userId);
            return !user.isUnresolvable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether the scope names the repository, a project Polarion knows, or a project group. */
    public boolean scopeExists(@NotNull Scope scope) {
        try {
            String projectId = scope.projectId();
            if (projectId != null) {
                IProject project = polarionService.getProjectService().getProject(projectId);
                return !project.isUnresolvable();
            }
            return scope.isGlobal() || projectGroup(scope) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private @Nullable IProjectGroup projectGroup(@NotNull Scope scope) {
        ILocation location = Location.getLocationWithRepository(IRepositoryService.DEFAULT, scope.path());
        IGroupEntity groupEntity = polarionService.getProjectService().getGroupEntityAtLocation(location);
        return groupEntity instanceof IProjectGroup projectGroup ? projectGroup : null;
    }

    /**
     * A value as one term of the query, whatever it holds: quoted, with the quote and the backslash
     * escaped. A value can then not carry query syntax, so the ids need no check of their characters.
     */
    static @NotNull String term(@NotNull String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    @NotNull String createWorkRecordsQuery(@NotNull String startDate, @NotNull String endDate) {
        return "(workRecords.date:[%s TO %s])"
                .formatted(startDate.replace("-", ""), endDate.replace("-", ""));
    }

    @Nullable String createUserWorkRecordsQuery(@NotNull List<String> userIds) {
        if (userIds.isEmpty()) {
            // Joining an empty list gives "", and the caller would add an empty "AND ()" group,
            // which Polarion rejects. No user filter means every user in the scope.
            return null;
        }
        return userIds.stream()
                .map(userId -> "workRecords.user.id:" + term(userId))
                .collect(Collectors.joining(" OR "));
    }

    private @Nullable String createScopeQuery(@NotNull Scope scope) {
        @Nullable String projectId = scope.projectId();
        if (projectId != null) {
            return "project.id:" + term(projectId);
        } else {
            if (scope.isGlobal()) {
                return null;
            } else {
                IProjectGroup projectGroup = projectGroup(scope);
                if (projectGroup != null) {
                    IPObjectList<IProject> projects = projectGroup.getDeepContainedProjects();
                    return projects.stream()
                            .map(IProject::getId)
                            .map(id -> "project.id:" + term(id))
                            .collect(Collectors.joining(" OR "));
                }
            }
        }
        // The scope of the request is not quoted back: the caller gets what it sent, and the
        // message is the one the REST response carries.
        throw new IllegalArgumentException("Can not create query for the provided scope");
    }

}
