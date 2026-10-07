package com.intechcore.polarion.extension.timesheet;

import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimesheetNavigationExtenderTest {

    private final TimesheetNavigationExtender extender = new TimesheetNavigationExtender();

    private static IContextId context(String name) {
        IContextId contextId = mock(IContextId.class);
        when(contextId.getContextName()).thenReturn(name);
        return contextId;
    }

    @Test
    void namesTheTopic() {
        assertThat(extender.getId()).isEqualTo("timesheet");
        assertThat(extender.getLabel()).isEqualTo("Timesheet");
        assertThat(extender.getIconUrl()).isEqualTo("/polarion/timesheet-app/ui/images/menu/30x30/_parent.svg");
        assertThat(extender.requiresToken()).isFalse();
        // A page, not a tree: the topic has no nodes of its own.
        assertThat(extender.getRootNodes(context("elibrary"))).isEmpty();
    }

    /** In a project the report shows that project only. */
    @Test
    void locksTheReportToItsProject() {
        assertThat(extender.getPageUrl(context("elibrary")))
                .isEqualTo("/polarion/timesheet-app/ui/app/index.html?feature=report&scope=elibrary&scopeLocked=true");
        assertThat(extender.getPageUrl(context("my project")))
                .isEqualTo("/polarion/timesheet-app/ui/app/index.html?feature=report&scope=my+project&scopeLocked=true");
    }

    /** In the repository the report opens on every project, and the scope may be changed. */
    @Test
    void opensOnTheRepositoryOutsideAProject() {
        assertThat(extender.getPageUrl(context(null))).isEqualTo("/polarion/timesheet-app/ui/app/index.html?feature=report&scope=%2F");
        assertThat(extender.getPageUrl(context(""))).isEqualTo("/polarion/timesheet-app/ui/app/index.html?feature=report&scope=%2F");
    }
}
