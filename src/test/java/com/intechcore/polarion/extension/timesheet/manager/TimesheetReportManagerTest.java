package com.intechcore.polarion.extension.timesheet.manager;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.WorkRecord;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.shared.api.Scope;
import com.polarion.alm.shared.api.model.wi.WorkItemReference;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.RunnableInReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkRecord;
import com.polarion.core.util.types.DateOnly;
import com.polarion.core.util.types.duration.DurationTime;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class TimesheetReportManagerTest {

    private static final String RENDERED_HTML = "<span class=\"polarion-no-style-cleanup\"><a class=\"polarion-Hyperlink\" href=\"#\">"
            + "<span><img src=\"/polarion/ria/images/enums/type_task.png\" class=\"polarion-Icons\"/></span>EL-1<span> - Write</span></a></span>";

    private PolarionService polarionService;
    private IDataService dataService;
    private TimesheetReportManager manager;
    private MockedStatic<TransactionalExecutor> transactions;

    @BeforeEach
    void setUp() {
        polarionService = mock(PolarionService.class, RETURNS_DEEP_STUBS);
        dataService = polarionService.getTrackerService().getDataService();
        manager = new TimesheetReportManager(polarionService);

        // The rendering runs inside a read-only transaction. Outside Polarion there is none, so the
        // callback is invoked here instead of being swallowed, which keeps the rendering under test.
        transactions = mockStatic(TransactionalExecutor.class);
        ReadOnlyTransaction transaction = mock(ReadOnlyTransaction.class);
        transactions.when(() -> TransactionalExecutor.executeSafelyInReadOnlyTransaction(any()))
                .thenAnswer(invocation -> ((RunnableInReadOnlyTransaction<?>) invocation.getArgument(0)).run(transaction));
    }

    @AfterEach
    void tearDown() {
        transactions.close();
    }

    // --- Fixtures ---

    private static IProject project(String id) {
        IProject project = mock(IProject.class);
        when(project.getId()).thenReturn(id);
        return project;
    }

    @SuppressWarnings("SameParameterValue")
    private static IWorkRecord record(String date, String userId, String userName, String projectId, String projectName,
                                      String workItemId, String workItemTitle, float hours) {
        IUser user = mock(IUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.getName()).thenReturn(userName);

        IProject project = mock(IProject.class);
        when(project.getName()).thenReturn(projectName);

        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getId()).thenReturn(workItemId);
        when(workItem.getTitle()).thenReturn(workItemTitle);

        DateOnly dateOnly = mock(DateOnly.class);
        when(dateOnly.toString()).thenReturn(date);

        DurationTime timeSpent = mock(DurationTime.class);
        when(timeSpent.getHours()).thenReturn(hours);

        IWorkRecord workRecord = mock(IWorkRecord.class);
        when(workRecord.getDate()).thenReturn(dateOnly);
        when(workRecord.getUser()).thenReturn(user);
        when(workRecord.getProjectId()).thenReturn(projectId);
        when(workRecord.getProject()).thenReturn(project);
        when(workRecord.getWorkItem()).thenReturn(workItem);
        when(workRecord.getTimeSpent()).thenReturn(timeSpent);
        return workRecord;
    }

    @SuppressWarnings("unchecked")
    private static IWorkItem workItemWith(IWorkRecord... records) {
        List<IWorkRecord> asList = Arrays.asList(records);
        IPObjectList<IWorkRecord> workRecords = mock(IPObjectList.class);
        when(workRecords.iterator()).thenReturn(asList.iterator());
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getWorkRecords()).thenReturn(workRecords);
        return workItem;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void searchReturns(IWorkItem... workItems) {
        List<IWorkItem> asList = Arrays.asList(workItems);
        IPObjectList found = mock(IPObjectList.class);
        when(found.size()).thenReturn(workItems.length);
        when(found.iterator()).thenReturn(asList.iterator());
        when(dataService.searchInstances(eq(IWorkItem.PROTO), anyString(), isNull())).thenReturn(found);
    }

    private String capturedQuery() {
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(dataService).searchInstances(eq(IWorkItem.PROTO), query.capture(), isNull());
        return query.getValue();
    }

    // --- The helpers the query is built from ---

    @Test
    void isWithinPeriod_inclusiveRange() {
        assertThat(manager.isWithinPeriod("2026-06-15", "2026-06-01", "2026-06-30")).isTrue();
        assertThat(manager.isWithinPeriod("2026-06-01", "2026-06-01", "2026-06-30")).isTrue(); // start boundary
        assertThat(manager.isWithinPeriod("2026-06-30", "2026-06-01", "2026-06-30")).isTrue(); // end boundary
    }

    @Test
    void isWithinPeriod_outOfRange() {
        assertThat(manager.isWithinPeriod("2026-05-31", "2026-06-01", "2026-06-30")).isFalse();
        assertThat(manager.isWithinPeriod("2026-07-01", "2026-06-01", "2026-06-30")).isFalse();
    }

    @Test
    void isWithinPeriod_toleratesMixedDateFormats() {
        assertThat(manager.isWithinPeriod("2026-06-15", "20260601", "20260630")).isTrue();
    }

    @Test
    void extractIconUrl_returnsFirstImgSrc() {
        String html = "<span class=\"polarion-no-style-cleanup\"><a class=\"polarion-Hyperlink\" href=\"#\">"
                + "<span><img src=\"/polarion/ria/images/enums/type_heading.png\" class=\"polarion-Icons\"/></span>"
                + "EL-7<span> - Abbreviations</span></a></span>";
        assertThat(manager.extractIconUrl(html)).isEqualTo("/polarion/ria/images/enums/type_heading.png");
    }

    @Test
    void extractIconUrl_nullOrNoImage() {
        assertThat(manager.extractIconUrl(null)).isNull();
        assertThat(manager.extractIconUrl("<a href=\"#\">EL-1</a>")).isNull();
    }

    @Test
    void createWorkRecordsQuery_stripsDashes() {
        assertThat(manager.createWorkRecordsQuery("2026-06-01", "2026-06-30"))
                .isEqualTo("(workRecords.date:[20260601 TO 20260630])");
    }

    @Test
    void createUserWorkRecordsQuery_withoutUsersIsNoFilter() {
        assertThat(manager.createUserWorkRecordsQuery(List.of())).isNull();
    }

    @Test
    void createQuery_withoutUsersSelectsEveryUser() {
        Scope scope = mock(Scope.class);
        when(scope.projectId()).thenReturn("elibrary");

        manager.getTimesheet(scope, List.of(), "2026-08-01", "2026-08-31");

        assertThat(capturedQuery()).isEqualTo("(workRecords.date:[20260801 TO 20260831]) AND (project.id:elibrary)");
    }

    /** The no-argument constructor is what the REST controllers use. */
    @Test
    void constructor_takesThePolarionServiceItself() {
        try (MockedConstruction<PolarionService> services = mockConstruction(PolarionService.class)) {
            assertThat(new TimesheetReportManager()).isNotNull();
            assertThat(services.constructed()).hasSize(1);
        }
    }

    @Test
    void createUserWorkRecordsQuery_joinsWithOr() {
        assertThat(manager.createUserWorkRecordsQuery(List.of("aSeller", "mTest")))
                .isEqualTo("workRecords.user.id:aSeller OR workRecords.user.id:mTest");
        assertThat(manager.createUserWorkRecordsQuery(List.of("solo")))
                .isEqualTo("workRecords.user.id:solo");
    }

    // --- The query the search is driven with ---

    @Test
    void createQuery_projectScope() {
        Scope scope = mock(Scope.class);
        when(scope.projectId()).thenReturn("elibrary");

        manager.getTimesheet(scope, List.of("aSeller"), "2026-08-01", "2026-08-31");

        assertThat(capturedQuery()).isEqualTo(
                "(workRecords.date:[20260801 TO 20260831]) AND (workRecords.user.id:aSeller) AND (project.id:elibrary)");
    }

    @Test
    void createQuery_globalScopeSelectsEveryProject() {
        Scope scope = mock(Scope.class);
        when(scope.isGlobal()).thenReturn(true);

        manager.getTimesheet(scope, List.of("aSeller", "mTest"), "2026-08-01", "2026-08-31");

        assertThat(capturedQuery()).isEqualTo(
                "(workRecords.date:[20260801 TO 20260831]) AND (workRecords.user.id:aSeller OR workRecords.user.id:mTest)");
    }

    @Test
    void createQuery_groupScopeListsTheProjectsOfTheGroup() {
        Scope scope = mock(Scope.class);
        when(scope.path()).thenReturn("/drafts");
        IProjectGroup group = mock(IProjectGroup.class);
        // Built before the stubbing: a mock created inside thenReturn(...) opens a second stubbing.
        Stream<IProject> contained = Stream.of(project("elibrary"), project("drafts"));
        IPObjectList<IProject> projects = mock(IPObjectList.class);
        when(projects.stream()).thenReturn(contained);
        when(group.getDeepContainedProjects()).thenReturn(projects);
        when(polarionService.getProjectService().getGroupEntityAtLocation(any())).thenReturn(group);

        manager.getTimesheet(scope, List.of("aSeller"), "2026-08-01", "2026-08-31");

        assertThat(capturedQuery()).endsWith("AND (project.id:elibrary OR project.id:drafts)");
    }

    /** A scope that is neither a project, nor global, nor a group has no query, and saying so beats guessing. */
    @Test
    void createQuery_rejectsAnUnknownScope() {
        Scope scope = mock(Scope.class);
        when(scope.path()).thenReturn("/nowhere");
        when(polarionService.getProjectService().getGroupEntityAtLocation(any())).thenReturn(null);

        assertThatThrownBy(() -> manager.getTimesheet(scope, List.of("aSeller"), "2026-08-01", "2026-08-31"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Can not create query for provided scope:");
    }

    // --- Turning the work records into the report ---

    @Test
    void getTimesheet_keepsTheRecordsOfTheSelectedUsersAndDates() {
        Scope scope = mock(Scope.class);
        when(scope.projectId()).thenReturn("elibrary");
        searchReturns(workItemWith(
                record("2026-08-03", "aSeller", "Adam Seller", "elibrary", "eLibrary", "EL-1", "Write", 2.5f),
                record("2026-08-04", "mTest", "Mary Test", "elibrary", "eLibrary", "EL-2", "Review", 1.5f)));

        Timesheet timesheet = manager.getTimesheet(scope, List.of("aSeller", "mTest"), "2026-08-01", "2026-08-31");

        assertThat(timesheet.getStartDate()).isEqualTo("2026-08-01");
        assertThat(timesheet.getFinishDate()).isEqualTo("2026-08-31");
        assertThat(timesheet.getWorkRecords()).hasSize(2);
        assertThat(timesheet.getTotalHours()).isEqualTo(4d);
        WorkRecord first = timesheet.getWorkRecords().getFirst();
        assertThat(first.getDate()).isEqualTo("2026-08-03");
        assertThat(first.getUser().getId()).isEqualTo("aSeller");
        assertThat(first.getUser().getName()).isEqualTo("Adam Seller");
        assertThat(first.getWorkItem().getId()).isEqualTo("EL-1");
        assertThat(first.getWorkItem().getTitle()).isEqualTo("Write");
        assertThat(first.getWorkItem().getProject().getId()).isEqualTo("elibrary");
        assertThat(first.getWorkItem().getProject().getName()).isEqualTo("eLibrary");
    }

    /**
     * The query selects work items that have a record in the period, and such an item may carry
     * records of other users or of other dates. Those must not reach the report.
     */
    @Test
    void getTimesheet_dropsRecordsOfOtherUsersAndOtherDates() {
        Scope scope = mock(Scope.class);
        when(scope.projectId()).thenReturn("elibrary");
        searchReturns(workItemWith(
                record("2026-08-03", "aSeller", "Adam Seller", "elibrary", "eLibrary", "EL-1", "Write", 2f),
                record("2026-08-03", "someoneElse", "Someone Else", "elibrary", "eLibrary", "EL-1", "Write", 3f),
                record("2026-07-31", "aSeller", "Adam Seller", "elibrary", "eLibrary", "EL-1", "Write", 4f),
                record("2026-09-01", "aSeller", "Adam Seller", "elibrary", "eLibrary", "EL-1", "Write", 5f)));

        Timesheet timesheet = manager.getTimesheet(scope, List.of("aSeller"), "2026-08-01", "2026-08-31");

        assertThat(timesheet.getWorkRecords()).hasSize(1);
        assertThat(timesheet.getTotalHours()).isEqualTo(2d);
    }

    @Test
    void getTimesheet_withoutAnyWorkItem() {
        Scope scope = mock(Scope.class);
        when(scope.projectId()).thenReturn("elibrary");
        searchReturns();

        Timesheet timesheet = manager.getTimesheet(scope, List.of("aSeller"), "2026-08-01", "2026-08-31");

        assertThat(timesheet.getWorkRecords()).isEmpty();
        // Nothing to render, so no transaction is opened.
        transactions.verifyNoInteractions();
    }

    /**
     * Polarion renders the work item the way its own report widgets do. The rendering is done once
     * per unique work item, inside one read-only transaction.
     */
    @Test
    void getTimesheet_rendersEachWorkItemOnce() {
        Scope scope = mock(Scope.class);
        when(scope.projectId()).thenReturn("elibrary");
        searchReturns(workItemWith(
                record("2026-08-03", "aSeller", "Adam Seller", "elibrary", "eLibrary", "EL-1", "Write", 1f),
                record("2026-08-04", "aSeller", "Adam Seller", "elibrary", "eLibrary", "EL-1", "Write", 1f)));

        Timesheet timesheet;
        try (MockedConstruction<WorkItemReference> references = mockConstruction(WorkItemReference.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                (reference, context) -> when(reference.get(any()).render().withLinks().withTitle().htmlFor().rpeView())
                        .thenReturn(RENDERED_HTML))) {
            timesheet = manager.getTimesheet(scope, List.of("aSeller"), "2026-08-01", "2026-08-31");

            // Two records, one work item: rendered once and reused.
            assertThat(references.constructed()).hasSize(1);
        }

        assertThat(timesheet.getWorkRecords()).allSatisfy(workRecord -> {
            assertThat(workRecord.getWorkItem().getHtml()).isEqualTo(RENDERED_HTML);
            assertThat(workRecord.getWorkItem().getIconUrl()).isEqualTo("/polarion/ria/images/enums/type_task.png");
        });
    }

    /**
     * A timesheet holding no records opens no transaction. Both shapes of "nothing to render" are
     * covered here, because getTimesheet itself always builds a list.
     */
    @Test
    void renderWorkItemsHtml_withoutRecordsDoesNothing() {
        manager.renderWorkItemsHtml(new Timesheet("2026-08-01", "2026-08-31", null));
        manager.renderWorkItemsHtml(new Timesheet("2026-08-01", "2026-08-31", List.of()));

        transactions.verifyNoInteractions();
    }

    /** A work item Polarion cannot render leaves the report without its html, not without the record. */
    @Test
    void getTimesheet_survivesAWorkItemThatCannotBeRendered() {
        Scope scope = mock(Scope.class);
        when(scope.projectId()).thenReturn("elibrary");
        searchReturns(workItemWith(record("2026-08-03", "aSeller", "Adam Seller", "elibrary", "eLibrary", "EL-1", "Write", 1f)));

        Timesheet timesheet;
        try (MockedConstruction<WorkItemReference> ignored = mockConstruction(WorkItemReference.class,
                (reference, context) -> when(reference.get(any())).thenThrow(new IllegalStateException("no transaction")))) {
            timesheet = manager.getTimesheet(scope, List.of("aSeller"), "2026-08-01", "2026-08-31");
        }

        assertThat(timesheet.getWorkRecords()).hasSize(1);
        assertThat(timesheet.getWorkRecords().getFirst().getWorkItem().getHtml()).isNull();
        assertThat(timesheet.getWorkRecords().getFirst().getWorkItem().getIconUrl()).isNull();
    }
}
