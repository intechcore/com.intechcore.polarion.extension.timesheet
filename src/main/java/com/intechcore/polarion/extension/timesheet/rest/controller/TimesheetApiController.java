package com.intechcore.polarion.extension.timesheet.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import com.intechcore.polarion.extension.timesheet.model.ScopeInfo;
import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.User;

import jakarta.ws.rs.Path;
import java.util.List;

@Secured
@Path("/api")
public class TimesheetApiController extends TimesheetInternalController {

    @Override
    public Timesheet getTimesheet(String userId, String startDate, String endDate, String scopePath) {
        return polarionService.callPrivileged(() -> super.getTimesheet(userId, startDate, endDate, scopePath));
    }

    @Override
    public Timesheet getTimesheetForUsers(String userIds, String startDate, String endDate, String scopePath) {
        return polarionService.callPrivileged(() -> super.getTimesheetForUsers(userIds, startDate, endDate, scopePath));
    }

    @Override
    public List<User> getUsers() {
        return polarionService.callPrivileged(super::getUsers);
    }

    @Override
    public List<ScopeInfo> getScopes() {
        return polarionService.callPrivileged(super::getScopes);
    }

}
