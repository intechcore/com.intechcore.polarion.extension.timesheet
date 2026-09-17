package com.intechcore.polarion.extension.timesheet.widget;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.model.rp.parameter.ParameterFactory;
import com.polarion.alm.shared.api.model.rp.parameter.RichPageParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetContext;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetRenderingContext;
import com.polarion.alm.shared.api.utils.collections.ReadOnlyStrictMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class TimesheetReportWidgetTest {

    private final TimesheetReportWidget widget = new TimesheetReportWidget();

    /**
     * The icon URL has to resolve inside the timesheet-app webapp, which is the only context
     * {@code TimesheetAppServlet} serves. A wrong prefix shows the placeholder icon in the widget
     * palette.
     */
    @Test
    void getIcon_pointsIntoTheAppWebapp() {
        assertThat(widget.getIcon(mock(RichPageWidgetContext.class)))
                .isEqualTo("/polarion/timesheet-app/ui/images/widget-icon.svg");
    }

    @Test
    void getLabel_andDetails() {
        assertThat(widget.getLabel(mock(SharedContext.class))).isEqualTo("Timesheet Report");
        assertThat(widget.getDetailsHtml(mock(RichPageWidgetContext.class))).isEqualTo("Generates a timesheet report");
    }

    /** The tag decides the category the widget appears under in the palette. */
    @Test
    void getTags_isReports() {
        assertThat(widget.getTags(mock(SharedContext.class))).containsExactly(TimesheetReportWidget.REPORTS);
    }

    /**
     * The renderer reads the parameters back by these keys, so the definition and
     * {@link TimesheetReportWidgetRenderer} have to agree on every one of them.
     */
    @Test
    void getParametersDefinition_carriesTheKeysTheRendererReads() {
        ParameterFactory parameterFactory = mock(ParameterFactory.class, RETURNS_DEEP_STUBS);

        ReadOnlyStrictMap<String, RichPageParameter> parameters;
        // The widget asks Polarion for the current user, which needs a running platform.
        try (MockedConstruction<PolarionService> ignored =
                     mockConstruction(PolarionService.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS))) {
            parameters = widget.getParametersDefinition(parameterFactory);
        }

        assertThat(parameters.get(TimesheetReportWidget.PARAMETER_SCOPE)).isNotNull();
        assertThat(parameters.get(TimesheetReportWidget.PARAMETER_USER_IDS)).isNotNull();
        assertThat(parameters.get(TimesheetReportWidget.COMPOSITE_PARAMETER_ADVANCED)).isNotNull();
    }

    /** The widget itself renders nothing: the whole markup comes from the renderer. */
    @Test
    void renderHtml_delegatesToTheRenderer() {
        try (MockedConstruction<TimesheetReportWidgetRenderer> renderers = mockConstruction(
                TimesheetReportWidgetRenderer.class,
                (renderer, context) -> when(renderer.render()).thenReturn("<iframe></iframe>"))) {

            assertThat(widget.renderHtml(mock(RichPageWidgetRenderingContext.class))).isEqualTo("<iframe></iframe>");
            assertThat(renderers.constructed()).hasSize(1);
        }
    }
}
