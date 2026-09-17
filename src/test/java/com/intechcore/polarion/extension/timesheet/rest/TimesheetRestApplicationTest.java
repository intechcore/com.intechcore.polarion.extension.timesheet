package com.intechcore.polarion.extension.timesheet.rest;

import com.intechcore.polarion.extension.timesheet.rest.controller.TimesheetApiController;
import com.intechcore.polarion.extension.timesheet.rest.controller.TimesheetInternalController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A controller missing from this set is not served, and the endpoint answers 404 on a running
 * server rather than failing the build.
 */
class TimesheetRestApplicationTest {

    @Test
    void registersBothControllers() {
        assertThat(new TimesheetRestApplication().getExtensionControllerClasses())
                .containsExactlyInAnyOrder(TimesheetApiController.class, TimesheetInternalController.class);
    }
}
