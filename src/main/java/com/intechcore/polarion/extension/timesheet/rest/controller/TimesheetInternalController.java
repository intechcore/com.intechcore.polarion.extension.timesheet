package com.intechcore.polarion.extension.timesheet.rest.controller;

import ch.sbb.polarion.extension.generic.properties.ConfigurationProperties;
import ch.sbb.polarion.extension.generic.properties.CurrentExtensionConfiguration;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager;
import com.intechcore.polarion.extension.timesheet.model.ScopeInfo;
import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.User;
import com.intechcore.polarion.extension.timesheet.util.ScopeFactoryImpl;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.shared.api.Scope;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Tag(name = "Timesheet reports")
@Hidden
@Path("/internal")
@Singleton
public class TimesheetInternalController {

    protected final PolarionService polarionService;

    public TimesheetInternalController() {
        this.polarionService = new PolarionService();
    }


    @Operation(summary = "Returns timesheet report for a user for a given period")
    @GET
    @Path("/users/{user_id}/timesheet")
    @Produces(MediaType.APPLICATION_JSON)
    public Timesheet getTimesheet(@PathParam("user_id") String userId, @QueryParam("start_date") String startDate, @QueryParam("end_date") String endDate, @QueryParam("scope_path") String scopePath) {
        Scope scope = new ScopeFactoryImpl().fromPath(scopePath);
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }

        return new TimesheetReportManager(polarionService)
                .getTimesheet(scope, List.of(userId), startDate, endDate);
    }

    @Operation(summary = "Returns timesheet report for several users for a given period")
    @GET
    @Path("/timesheet")
    @Produces(MediaType.APPLICATION_JSON)
    public Timesheet getTimesheetForUsers(@QueryParam("user_ids") String userIds, @QueryParam("start_date") String startDate, @QueryParam("end_date") String endDate, @QueryParam("scope_path") String scopePath) {
        Scope scope = new ScopeFactoryImpl().fromPath(scopePath);
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }

        List<String> users = userIds == null || userIds.isBlank()
                ? List.of()
                : Arrays.stream(userIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        return new TimesheetReportManager(polarionService)
                .getTimesheet(scope, users, startDate, endDate);
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
        scopes.add(new ScopeInfo("/", "Repository (all projects)", "root", 0));
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
            if (seenPaths.add(containedProject.getId())) {
                scopes.add(new ScopeInfo(containedProject.getId(), containedProject.getName(), "project", depth));
            }
        }
    }


}
