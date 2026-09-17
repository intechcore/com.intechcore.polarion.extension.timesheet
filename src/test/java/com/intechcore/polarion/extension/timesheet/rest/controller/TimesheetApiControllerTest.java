package com.intechcore.polarion.extension.timesheet.rest.controller;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * The /api controller is the token-authenticated twin of the internal one. It adds nothing but the
 * privileged call, which is what lets a token request read data the calling user may not see.
 */
class TimesheetApiControllerTest {

    private MockedConstruction<PolarionService> services;
    private PolarionService polarionService;
    private TimesheetApiController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        services = mockConstruction(PolarionService.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
        controller = new TimesheetApiController();
        polarionService = services.constructed().getFirst();
        // Run what is handed to the privileged call, so the delegation is exercised rather than stubbed away.
        when(polarionService.callPrivileged(any(Callable.class)))
                .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
    }

    @AfterEach
    void tearDown() {
        services.close();
    }

    @SuppressWarnings("unchecked")
    private void verifyPrivilegedCall() {
        verify(polarionService, times(1)).callPrivileged(any(Callable.class));
    }

    @Test
    void getTimesheet_runsPrivileged() {
        assertThat(controller.getTimesheet("aSeller", "2026-08-01", "2026-08-31", "elibrary").getStartDate())
                .isEqualTo("2026-08-01");
        verifyPrivilegedCall();
    }

    @Test
    void getTimesheetForUsers_runsPrivileged() {
        assertThat(controller.getTimesheetForUsers("aSeller,mTest", "2026-08-01", "2026-08-31", "/").getFinishDate())
                .isEqualTo("2026-08-31");
        verifyPrivilegedCall();
    }

    @Test
    void getUsers_runsPrivileged() {
        assertThat(controller.getUsers()).isEmpty();
        verifyPrivilegedCall();
    }

    @Test
    void getScopes_runsPrivileged() {
        assertThat(controller.getScopes()).extracting("path").containsExactly("/");
        verifyPrivilegedCall();
    }
}
