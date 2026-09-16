package com.intechcore.polarion.extension.timesheet.rest;

import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import com.intechcore.polarion.extension.timesheet.rest.controller.TimesheetApiController;
import com.intechcore.polarion.extension.timesheet.rest.controller.TimesheetInternalController;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class TimesheetRestApplication extends GenericRestApplication {

    @Override
    protected @NotNull Set<Class<?>> getExtensionControllerClasses() {
        return Set.of(
                TimesheetApiController.class,
                TimesheetInternalController.class
        );
    }
}
