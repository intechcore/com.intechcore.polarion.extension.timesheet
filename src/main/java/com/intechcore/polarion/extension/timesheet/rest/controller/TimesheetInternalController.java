package com.intechcore.polarion.extension.timesheet.rest.controller;

import ch.sbb.polarion.extension.generic.properties.ConfigurationProperties;
import ch.sbb.polarion.extension.generic.properties.CurrentExtensionConfiguration;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.timesheet.manager.ReportStateStore;
import com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager;
import com.intechcore.polarion.extension.timesheet.model.ReportState;
import com.intechcore.polarion.extension.timesheet.model.ScopeInfo;
import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.User;
import com.intechcore.polarion.extension.timesheet.util.RequestValidator;
import com.intechcore.polarion.extension.timesheet.util.ScopeFactoryImpl;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.shared.api.Scope;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Tag(name = "Timesheet reports")
@Hidden
@Path("/internal")
@Singleton
public class TimesheetInternalController {

    /** The name of the root scope, here and in the PDF a widget writes on the server. */
    public static final String REPOSITORY_SCOPE_NAME = "Repository (all projects)";

    protected final PolarionService polarionService;

    public TimesheetInternalController() {
        this.polarionService = new PolarionService();
    }


    @Operation(summary = "Returns timesheet report for a user for a given period")
    @GET
    @Path("/users/{user_id}/timesheet")
    @Produces(MediaType.APPLICATION_JSON)
    public Timesheet getTimesheet(@PathParam("user_id") String userId,
                                  @Parameter(required = true) @QueryParam("start_date") String startDate,
                                  @Parameter(required = true) @QueryParam("end_date") String endDate,
                                  @QueryParam("scope_path") String scopePath) {
        RequestValidator.validatePeriod(startDate, endDate);
        Scope scope = new ScopeFactoryImpl().fromPath(RequestValidator.validateScopePath(scopePath));
        List<String> users = List.of(RequestValidator.validateUserId(userId));

        TimesheetReportManager manager = new TimesheetReportManager(polarionService);
        requireExisting(manager, scope, users);
        return manager.getTimesheet(scope, users, startDate, endDate);
    }

    @Operation(summary = "Returns timesheet report for several users for a given period")
    @GET
    @Path("/timesheet")
    @Produces(MediaType.APPLICATION_JSON)
    public Timesheet getTimesheetForUsers(@Parameter(required = true) @QueryParam("user_ids") String userIds,
                                          @Parameter(required = true) @QueryParam("start_date") String startDate,
                                          @Parameter(required = true) @QueryParam("end_date") String endDate,
                                          @QueryParam("scope_path") String scopePath) {
        RequestValidator.validatePeriod(startDate, endDate);
        Scope scope = new ScopeFactoryImpl().fromPath(RequestValidator.validateScopePath(scopePath));
        List<String> users = RequestValidator.validateUserIds(userIds);

        TimesheetReportManager manager = new TimesheetReportManager(polarionService);
        requireExisting(manager, scope, users);
        return manager.getTimesheet(scope, users, startDate, endDate);
    }

    /**
     * The validator bounds a request and the query quotes every value, so what is left to refuse is a
     * name Polarion does not know. The message does not quote it.
     */
    private static void requireExisting(@NotNull TimesheetReportManager manager, @NotNull Scope scope, @NotNull List<String> userIds) {
        if (!manager.scopeExists(scope)) {
            throw new IllegalArgumentException("Scope does not exist");
        }
        if (!userIds.stream().allMatch(manager::userExists)) {
            throw new IllegalArgumentException("User does not exist");
        }
    }

    @Operation(summary = "Returns the id and name of the current user")
    @GET
    @Path("/current-user")
    @Produces(MediaType.APPLICATION_JSON)
    public @Nullable User getCurrentUser() {
        // Only the id is needed (to preselect the user); the display name comes from /users.
        // Resolving IUser.getName() here fails outside a data transaction, so it is avoided.
        String userId = polarionService.getSecurityService().getCurrentUser();
        return (userId == null || userId.isBlank()) ? null : new User(userId, userId);
    }

    /**
     * Keeps what the report shows on screen for the current user, so a PDF export of the page writes
     * the same report: the export renders the widget on the server, where the report has no browser.
     */
    @Hidden
    @PUT
    @Path("/report-state/{state_key}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void saveReportState(@PathParam("state_key") String stateKey, ReportState state) {
        String key = RequestValidator.validateStateKey(stateKey);
        ReportState valid = RequestValidator.validateReportState(state);
        // A scope or a user that does not exist would fail the PDF export that reads this selection.
        requireExisting(new TimesheetReportManager(polarionService), new ScopeFactoryImpl().fromPath(valid.getScopePath()),
                List.of(valid.getUserIds().split(",")));
        String userId = polarionService.getSecurityService().getCurrentUser();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("No user is signed in");
        }
        ReportStateStore.getInstance().save(userId, key, valid);
    }

    @Operation(summary = "Returns all enabled Polarion users")
    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> getUsers() {
        List<User> users = new ArrayList<>();
        for (IUser user : polarionService.getProjectService().getUsers()) {
            if (!user.isDisabled()) {
                users.add(new User(user.getId(), user.getName() == null ? user.getId() : user.getName()));
            }
        }
        users.sort(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER));
        return users;
    }

    @Operation(summary = "Returns the selectable report scopes (root, project groups and projects)")
    @GET
    @Path("/scopes")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ScopeInfo> getScopes() {
        List<ScopeInfo> scopes = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        scopes.add(new ScopeInfo("/", REPOSITORY_SCOPE_NAME, "root", 0));
        seenPaths.add("/");
        IProjectGroup root = polarionService.getProjectService().getRootProjectGroup();
        if (root != null) {
            collectScopes(root, 1, scopes, seenPaths);
        }
        return scopes;
    }

    private void collectScopes(@NotNull IProjectGroup group, int depth, @NotNull List<ScopeInfo> scopes, @NotNull Set<String> seenPaths) {
        for (IProjectGroup child : group.getContainedGroups()) {
            String path = child.getLocation().getLocationPath();
            if (seenPaths.add(path)) {
                scopes.add(new ScopeInfo(path, child.getName(), "group", depth));
            }
            collectScopes(child, depth + 1, scopes, seenPaths);
        }
        for (Object project : group.getContainedProjects()) {
            IProject containedProject = (IProject) project;
            // A project the user may not read throws on getName(), which failed the whole list.
            if (containedProject.can().read() && seenPaths.add(containedProject.getId())) {
                scopes.add(new ScopeInfo(containedProject.getId(), containedProject.getName(), "project", depth));
            }
        }
    }


}
