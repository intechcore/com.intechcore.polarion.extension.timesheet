package com.intechcore.polarion.extension.timesheet.rest.controller;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.timesheet.model.ScopeInfo;
import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.User;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.projects.model.IUser;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class TimesheetInternalControllerTest {

    private MockedConstruction<PolarionService> services;
    private PolarionService polarionService;
    private TimesheetInternalController controller;

    @BeforeEach
    void setUp() {
        // The controller builds its own PolarionService, which needs a running platform.
        services = mockConstruction(PolarionService.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
        controller = new TimesheetInternalController();
        polarionService = services.constructed().getFirst();
    }

    @AfterEach
    void tearDown() {
        services.close();
    }

    @SuppressWarnings("unchecked")
    private static <T extends IPObject> IPObjectList<T> objectList(List<T> items) {
        IPObjectList<T> list = mock(IPObjectList.class);
        when(list.iterator()).thenReturn(items.iterator());
        when(list.size()).thenReturn(items.size());
        return list;
    }

    private static IUser user(String id, String name, boolean disabled) {
        IUser user = mock(IUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn(name);
        when(user.isDisabled()).thenReturn(disabled);
        return user;
    }

    private static IProject project(String id, String name) {
        IProject project = mock(IProject.class);
        when(project.getId()).thenReturn(id);
        when(project.getName()).thenReturn(name);
        return project;
    }

    private static IProjectGroup group(String path, String name, List<IProjectGroup> children, List<IProject> projects) {
        // Every mock is built before the stubbing starts: one created inside thenReturn(...) opens
        // a second stubbing while the first is still open, which Mockito rejects.
        IPObjectList<IProjectGroup> containedGroups = objectList(children);
        IPObjectList<IProject> containedProjects = objectList(projects);

        IProjectGroup group = mock(IProjectGroup.class, RETURNS_DEEP_STUBS);
        when(group.getName()).thenReturn(name);
        when(group.getLocation().getLocationPath()).thenReturn(path);
        when(group.getContainedGroups()).thenReturn(containedGroups);
        when(group.getContainedProjects()).thenReturn(containedProjects);
        return group;
    }

    // --- The period is mandatory on both timesheet endpoints ---

    @Test
    void getTimesheet_needsBothDates() {
        assertThatThrownBy(() -> controller.getTimesheet("aSeller", null, "2026-08-31", "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date and end date are required");
        assertThatThrownBy(() -> controller.getTimesheet("aSeller", "2026-08-01", null, "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date and end date are required");
    }

    @Test
    void getTimesheetForUsers_needsBothDates() {
        assertThatThrownBy(() -> controller.getTimesheetForUsers("aSeller", null, "2026-08-31", "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date and end date are required");
        assertThatThrownBy(() -> controller.getTimesheetForUsers("aSeller", "2026-08-01", null, "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date and end date are required");
    }

    @Test
    void getTimesheet_answersForThePeriodAsked() {
        Timesheet timesheet = controller.getTimesheet("aSeller", "2026-08-01", "2026-08-31", "elibrary");

        assertThat(timesheet.getStartDate()).isEqualTo("2026-08-01");
        assertThat(timesheet.getFinishDate()).isEqualTo("2026-08-31");
    }

    @Test
    void getTimesheetForUsers_answersForThePeriodAsked() {
        Timesheet timesheet = controller.getTimesheetForUsers("aSeller,mTest", "2026-08-01", "2026-08-31", "/");

        assertThat(timesheet.getStartDate()).isEqualTo("2026-08-01");
        assertThat(timesheet.getFinishDate()).isEqualTo("2026-08-31");
    }

    /** The user list arrives as one query parameter, and a stray comma or space must not become a user. */
    @Test
    void getTimesheetForUsers_toleratesAMessyUserList() {
        assertThat(controller.getTimesheetForUsers(" aSeller , ,mTest, ", "2026-08-01", "2026-08-31", "/")).isNotNull();
    }

    // --- What the request is checked for (RequestValidatorTest covers the rules themselves) ---

    /** Every user of the repository over an open period is one request against the whole server. */
    @Test
    void getTimesheetForUsers_needsAUserToReportOn() {
        assertThatThrownBy(() -> controller.getTimesheetForUsers(null, "2026-08-01", "2026-08-31", "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one user id is required");
        assertThatThrownBy(() -> controller.getTimesheetForUsers("   ", "2026-08-01", "2026-08-31", "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one user id is required");
    }

    @Test
    void getTimesheetForUsers_boundsThePeriod() {
        assertThatThrownBy(() -> controller.getTimesheetForUsers("aSeller", "2026-01-01", "2027-06-01", "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Period must not exceed 366 days");
    }

    /** The parameters reach a Lucene query, so query syntax in them is refused, not escaped. */
    @Test
    void bothEndpoints_refuseQuerySyntax() {
        assertThatThrownBy(() -> controller.getTimesheetForUsers("aSeller OR mTest", "2026-08-01", "2026-08-31", "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User id holds characters which are not allowed");
        assertThatThrownBy(() -> controller.getTimesheet("aSeller", "2026-08-01] OR project.id:[* TO *", "2026-08-31", "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date must be an ISO date (yyyy-MM-dd)");
        assertThatThrownBy(() -> controller.getTimesheet("aSeller", "2026-08-01", "2026-08-31", "elibrary OR project.id:secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Scope path holds characters which are not allowed");
    }

    // --- The current user ---

    @Test
    void getCurrentUser_carriesTheIdTwice() {
        when(polarionService.getSecurityService().getCurrentUser()).thenReturn("aSeller");

        User user = controller.getCurrentUser();

        assertThat(user).isNotNull();
        assertThat(user.getId()).isEqualTo("aSeller");
        // The display name comes from /users; resolving it here needs a data transaction.
        assertThat(user.getName()).isEqualTo("aSeller");
    }

    @Test
    void getCurrentUser_withoutASessionIsNull() {
        when(polarionService.getSecurityService().getCurrentUser()).thenReturn(null);
        assertThat(controller.getCurrentUser()).isNull();

        when(polarionService.getSecurityService().getCurrentUser()).thenReturn("  ");
        assertThat(controller.getCurrentUser()).isNull();
    }

    // --- The user list ---

    @Test
    void getUsers_dropsTheDisabledOnesAndSortsByName() {
        IPObjectList<IUser> all = objectList(List.of(
                user("mTest", "mary test", false),
                user("zUser", "Zoe Last", false),
                user("aSeller", "Adam Seller", false),
                user("gone", "Gone User", true)));
        when(polarionService.getProjectService().getUsers()).thenReturn(all);

        List<User> users = controller.getUsers();

        assertThat(users).extracting(User::getId).containsExactly("aSeller", "mTest", "zUser");
    }

    /** A user without a display name still has to be selectable, so the id stands in for it. */
    @Test
    void getUsers_fallsBackToTheIdAsTheName() {
        IPObjectList<IUser> all = objectList(List.of(user("nameless", null, false)));
        when(polarionService.getProjectService().getUsers()).thenReturn(all);

        assertThat(controller.getUsers()).singleElement().satisfies(user -> {
            assertThat(user.getId()).isEqualTo("nameless");
            assertThat(user.getName()).isEqualTo("nameless");
        });
    }

    // --- The scope tree ---

    @Test
    void getScopes_withoutARootGroupOffersTheRepository() {
        when(polarionService.getProjectService().getRootProjectGroup()).thenReturn(null);

        assertThat(controller.getScopes()).singleElement().satisfies(scope -> {
            assertThat(scope.getPath()).isEqualTo("/");
            assertThat(scope.getType()).isEqualTo("root");
            assertThat(scope.getDepth()).isZero();
        });
    }

    @Test
    void getScopes_walksTheGroupsAndTheirProjects() {
        IProjectGroup nested = group("/drafts/inner", "Inner", List.of(), List.of(project("nestedProject", "Nested")));
        IProjectGroup drafts = group("/drafts", "Drafts", List.of(nested), List.of(project("elibrary", "eLibrary")));
        IProjectGroup root = group("/", "Root", List.of(drafts), List.of());

        when(polarionService.getProjectService().getRootProjectGroup()).thenReturn(root);

        List<ScopeInfo> scopes = controller.getScopes();

        assertThat(scopes).extracting(ScopeInfo::getPath)
                .containsExactly("/", "/drafts", "/drafts/inner", "nestedProject", "elibrary");
        assertThat(scopes).extracting(ScopeInfo::getType)
                .containsExactly("root", "group", "group", "project", "project");
        // The depth is the nesting of the group a scope sits in: the project of the inner group is
        // one level deeper than that group, and the project of "/drafts" sits at its own level.
        assertThat(scopes).extracting(ScopeInfo::getDepth)
                .containsExactly(0, 1, 2, 3, 2);
    }

    /** The same project or group reachable twice must appear once: it is one selectable scope. */
    @Test
    void getScopes_listsEachPathOnce() {
        IProject shared = project("shared", "Shared");
        IProjectGroup first = group("/same", "First", List.of(), List.of(shared));
        IProjectGroup second = group("/same", "Second", List.of(), List.of(project("shared", "Shared again")));
        IProjectGroup root = group("/", "Root", Arrays.asList(first, second), List.of());
        when(polarionService.getProjectService().getRootProjectGroup()).thenReturn(root);

        List<ScopeInfo> scopes = controller.getScopes();

        assertThat(scopes).extracting(ScopeInfo::getPath).containsExactly("/", "/same", "shared");
    }
}
