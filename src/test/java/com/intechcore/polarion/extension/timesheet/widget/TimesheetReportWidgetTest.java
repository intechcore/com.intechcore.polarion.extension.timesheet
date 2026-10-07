package com.intechcore.polarion.extension.timesheet.widget;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.model.rp.parameter.BooleanParameter;
import com.polarion.alm.shared.api.model.rp.parameter.CustomEnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.ParameterFactory;
import com.polarion.alm.shared.api.model.rp.parameter.RichPageParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetContext;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetDependenciesContext;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetRenderingContext;
import com.polarion.alm.shared.api.utils.collections.ReadOnlyStrictMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        assertThat(parameters.get(TimesheetReportWidget.PARAMETER_USERS_MODE)).isNotNull();
        // "Selected users" by default: a new widget shows the users of its settings, as it always did.
        verify(parameterFactory.customEnum(TimesheetReportWidget.USERS_MODE)
                .addEnumItem(TimesheetReportWidget.USERS_SELECTED, "Selected users")
                .addEnumItem(TimesheetReportWidget.USERS_VIEWER, "Viewer of the page"))
                .singleValue(TimesheetReportWidget.USERS_SELECTED);
        assertThat(parameters.get(TimesheetReportWidget.PARAMETER_PERIOD)).isNotNull();
        assertThat(parameters.get(TimesheetReportWidget.PARAMETER_PERIOD_FROM)).isNotNull();
        assertThat(parameters.get(TimesheetReportWidget.PARAMETER_PERIOD_TO)).isNotNull();
        assertThat(parameters.get(TimesheetReportWidget.PARAMETER_HIDE_CONTROLS)).isNotNull();
        // Every "Allow changing" is on by default: a new widget locks nothing.
        for (String allow : List.of(TimesheetReportWidget.ALLOW_SCOPE, TimesheetReportWidget.ALLOW_USERS, TimesheetReportWidget.ALLOW_PERIOD)) {
            verify(parameterFactory.bool(allow)).value(true);
        }
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

    // --- The settings show only what applies ---

    /** Runs the dependencies for one state of the settings and returns what each parameter was set to. */
    private Map<String, Boolean> visibility(String usersMode, String period, boolean hideControls) {
        RichPageWidgetDependenciesContext context = mock(RichPageWidgetDependenciesContext.class);
        CustomEnumParameter usersModeParameter = mock(CustomEnumParameter.class);
        when(usersModeParameter.singleValue()).thenReturn(usersMode);
        CustomEnumParameter periodParameter = mock(CustomEnumParameter.class);
        when(periodParameter.singleValue()).thenReturn(period);
        BooleanParameter hideControlsParameter = mock(BooleanParameter.class);
        when(hideControlsParameter.value()).thenReturn(hideControls);
        when(context.<CustomEnumParameter>parameter(TimesheetReportWidget.PARAMETER_USERS_MODE)).thenReturn(usersModeParameter);
        when(context.<CustomEnumParameter>parameter(TimesheetReportWidget.PARAMETER_PERIOD)).thenReturn(periodParameter);
        when(context.<BooleanParameter>parameter(TimesheetReportWidget.PARAMETER_HIDE_CONTROLS)).thenReturn(hideControlsParameter);

        List<String> targets = List.of(TimesheetReportWidget.PARAMETER_USER_IDS, TimesheetReportWidget.PARAMETER_PERIOD_FROM,
                TimesheetReportWidget.PARAMETER_PERIOD_TO, TimesheetReportWidget.PARAMETER_ALLOW_SCOPE,
                TimesheetReportWidget.PARAMETER_ALLOW_USERS, TimesheetReportWidget.PARAMETER_ALLOW_PERIOD);
        Map<String, RichPageParameter> parameters = new HashMap<>();
        for (String target : targets) {
            RichPageParameter parameter = mock(RichPageParameter.class, RETURNS_DEEP_STUBS);
            parameters.put(target, parameter);
            when(context.<RichPageParameter>parameter(target)).thenReturn(parameter);
        }

        widget.processParameterDependencies(context);

        Map<String, Boolean> result = new HashMap<>();
        for (String target : targets) {
            ArgumentCaptor<Boolean> visible = ArgumentCaptor.forClass(Boolean.class);
            verify(parameters.get(target).visuals()).setVisible(visible.capture());
            result.put(target, visible.getValue());
        }
        return result;
    }

    /** The defaults: the users of the settings, the current month, the controls shown. */
    @Test
    void processParameterDependencies_showsTheUsersAndHidesTheDatesByDefault() {
        assertThat(visibility(null, TimesheetReportWidget.PERIOD_CURRENT_MONTH, false)).isEqualTo(Map.of(
                TimesheetReportWidget.PARAMETER_USER_IDS, true,
                TimesheetReportWidget.PARAMETER_PERIOD_FROM, false,
                TimesheetReportWidget.PARAMETER_PERIOD_TO, false,
                TimesheetReportWidget.PARAMETER_ALLOW_SCOPE, true,
                TimesheetReportWidget.PARAMETER_ALLOW_USERS, true,
                TimesheetReportWidget.PARAMETER_ALLOW_PERIOD, true));
    }

    /** The viewer needs no list of users, and a custom period needs its dates. */
    @Test
    void processParameterDependencies_hidesTheUsersForTheViewerAndShowsTheDatesForACustomPeriod() {
        Map<String, Boolean> visible = visibility(TimesheetReportWidget.USERS_VIEWER, TimesheetReportWidget.PERIOD_CUSTOM, false);

        assertThat(visible.get(TimesheetReportWidget.PARAMETER_USER_IDS)).isFalse();
        assertThat(visible.get(TimesheetReportWidget.PARAMETER_PERIOD_FROM)).isTrue();
        assertThat(visible.get(TimesheetReportWidget.PARAMETER_PERIOD_TO)).isTrue();
    }

    /** With the controls hidden there is nothing left to allow or lock. */
    @Test
    void processParameterDependencies_hidesTheAllowOptionsWithTheControls() {
        Map<String, Boolean> visible = visibility(TimesheetReportWidget.USERS_SELECTED, TimesheetReportWidget.PERIOD_CURRENT_MONTH, true);

        assertThat(visible.get(TimesheetReportWidget.PARAMETER_ALLOW_SCOPE)).isFalse();
        assertThat(visible.get(TimesheetReportWidget.PARAMETER_ALLOW_USERS)).isFalse();
        assertThat(visible.get(TimesheetReportWidget.PARAMETER_ALLOW_PERIOD)).isFalse();
        assertThat(visible.get(TimesheetReportWidget.PARAMETER_USER_IDS)).isTrue();
    }
}
