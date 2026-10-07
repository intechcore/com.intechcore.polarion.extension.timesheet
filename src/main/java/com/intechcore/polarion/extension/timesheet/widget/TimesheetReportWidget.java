package com.intechcore.polarion.extension.timesheet.widget;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.model.rp.parameter.*;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidget;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetContext;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetDependenciesContext;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetRenderingContext;
import com.polarion.alm.shared.api.utils.collections.ImmutableStrictList;
import com.polarion.alm.shared.api.utils.collections.ReadOnlyStrictMap;
import com.polarion.alm.shared.api.utils.collections.StrictMap;
import com.polarion.alm.shared.api.utils.collections.StrictMapImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TimesheetReportWidget extends RichPageWidget {

    public static final String REPORTS = "Reports";
    public static final String SCOPE = "Scope";
    public static final String USERS = "Users";
    public static final String USERS_MODE = "Show hours of";
    public static final String PERIOD = "Period";
    public static final String PERIOD_FROM = "From";
    public static final String PERIOD_TO = "To";
    public static final String HIDE_CONTROLS = "Hide controls";
    public static final String ALLOW_SCOPE = "Allow changing scope";
    public static final String ALLOW_USERS = "Allow changing users";
    public static final String ALLOW_PERIOD = "Allow changing period";
    public static final String WORKING_DAY_IN_HOURS = "Working day in hours";
    public static final int FULL_TIME_HOURS = 8;

    public static final String PARAMETER_SCOPE = "scope";
    public static final String PARAMETER_USER_IDS = "userIds";
    public static final String PARAMETER_USERS_MODE = "usersMode";
    public static final String PARAMETER_PERIOD = "period";
    public static final String PARAMETER_PERIOD_FROM = "periodFrom";
    public static final String PARAMETER_PERIOD_TO = "periodTo";
    public static final String PARAMETER_HIDE_CONTROLS = "hideControls";
    public static final String PARAMETER_ALLOW_SCOPE = "allowScope";
    public static final String PARAMETER_ALLOW_USERS = "allowUsers";
    public static final String PARAMETER_ALLOW_PERIOD = "allowPeriod";

    public static final String USERS_VIEWER = "viewer";
    public static final String USERS_SELECTED = "selected";

    public static final String PERIOD_CURRENT_MONTH = "current-month";
    public static final String PERIOD_PREVIOUS_MONTH = "previous-month";
    public static final String PERIOD_CUSTOM = "custom";
    public static final String COMPOSITE_PARAMETER_ADVANCED = "Advanced";
    public static final String PARAMETER_WORKING_DAY_IN_HOURS = "workingDayInHours";

    @Override
    public @NotNull String getIcon(@NotNull RichPageWidgetContext richPageWidgetContext) {
        return "/polarion/timesheet-app/ui/images/widget-icon.svg";
    }

    @Override
    public @NotNull String getLabel(@NotNull SharedContext sharedContext) {
        return "Timesheet Report";
    }

    @Override
    public @NotNull String getDetailsHtml(@NotNull RichPageWidgetContext richPageWidgetContext) {
        return "Generates a timesheet report";
    }

    @Override
    public @NotNull ReadOnlyStrictMap<String, RichPageParameter> getParametersDefinition(@NotNull ParameterFactory parameterFactory) {
        // The widget presets the defaults shown when the report opens: scope, users and period.
        ScopeParameter scopeParameter = parameterFactory.scope(SCOPE).build();

        String currentUser = new PolarionService().getSecurityService().getCurrentUser();
        EnumParameter usersParameter = parameterFactory.enumeration(USERS, "@user")
                .dependencyTarget(true).dependencySource(true)
                .allowMultipleValues(true)
                .values(currentUser)
                .build();

        StrictMap<String, RichPageParameter> parameters = new StrictMapImpl<>();
        // The viewer, whoever set the widget up, or the users below. One choice rather than a flag
        // beside the list: a flag left the list filled in and silently ignored.
        CustomEnumParameter usersModeParameter = parameterFactory.customEnum(USERS_MODE)
                .addEnumItem(USERS_SELECTED, "Selected users")
                .addEnumItem(USERS_VIEWER, "Viewer of the page")
                .singleValue(USERS_SELECTED)
                .dependencySource(true)
                .build();

        // Each "Allow changing" keeps or locks one control of the report, so they combine freely: a
        // report fixed to the page scope that still lets the viewer add colleagues, for example.
        parameters.put(PARAMETER_SCOPE, scopeParameter);
        parameters.put(PARAMETER_ALLOW_SCOPE, allowParameter(parameterFactory, ALLOW_SCOPE));
        parameters.put(PARAMETER_USERS_MODE, usersModeParameter);
        parameters.put(PARAMETER_USER_IDS, usersParameter);
        parameters.put(PARAMETER_ALLOW_USERS, allowParameter(parameterFactory, ALLOW_USERS));

        // The months are counted by the browser of the viewer, when the report opens, so the widget
        // passes only which one. The dates are read for a custom period only.
        CustomEnumParameter periodParameter = parameterFactory.customEnum(PERIOD)
                .addEnumItem(PERIOD_CURRENT_MONTH, "Current month")
                .addEnumItem(PERIOD_PREVIOUS_MONTH, "Previous month")
                .addEnumItem(PERIOD_CUSTOM, "Custom")
                .singleValue(PERIOD_CURRENT_MONTH)
                .dependencySource(true)
                .build();
        parameters.put(PARAMETER_PERIOD, periodParameter);
        parameters.put(PARAMETER_PERIOD_FROM, parameterFactory.date(PERIOD_FROM).dependencyTarget(true).build());
        parameters.put(PARAMETER_PERIOD_TO, parameterFactory.date(PERIOD_TO).dependencyTarget(true).build());
        parameters.put(PARAMETER_ALLOW_PERIOD, allowParameter(parameterFactory, ALLOW_PERIOD));

        // The page shows the tables only: no title, no scope, users, period or export.
        parameters.put(PARAMETER_HIDE_CONTROLS, parameterFactory.bool(HIDE_CONTROLS).value(false).dependencySource(true).build());

        IntegerParameter workingDayInHoursParameter = parameterFactory.integer(WORKING_DAY_IN_HOURS).value(FULL_TIME_HOURS).build();
        CompositeParameter advancedCompositeParameter = parameterFactory.composite(COMPOSITE_PARAMETER_ADVANCED)
                .collapsedByDefault(true)
                .add(PARAMETER_WORKING_DAY_IN_HOURS, workingDayInHoursParameter)
                .build();
        parameters.put(COMPOSITE_PARAMETER_ADVANCED, advancedCompositeParameter);

        return parameters;
    }

    /** On by default: a new widget locks nothing. Hidden with the controls it would lock. */
    private static @NotNull BooleanParameter allowParameter(@NotNull ParameterFactory parameterFactory, @NotNull String label) {
        return parameterFactory.bool(label).value(true).dependencyTarget(true).build();
    }

    /**
     * Shows only the settings that apply: the users for "Selected users", the dates for a custom
     * period, and the "Allow changing" options while there are controls to allow.
     */
    @Override
    public void processParameterDependencies(@NotNull RichPageWidgetDependenciesContext context) {
        super.processParameterDependencies(context);

        CustomEnumParameter usersMode = context.parameter(PARAMETER_USERS_MODE);
        setVisible(context, PARAMETER_USER_IDS, !USERS_VIEWER.equals(usersMode.singleValue()));

        CustomEnumParameter period = context.parameter(PARAMETER_PERIOD);
        boolean custom = PERIOD_CUSTOM.equals(period.singleValue());
        setVisible(context, PARAMETER_PERIOD_FROM, custom);
        setVisible(context, PARAMETER_PERIOD_TO, custom);

        BooleanParameter hideControls = context.parameter(PARAMETER_HIDE_CONTROLS);
        for (String allow : List.of(PARAMETER_ALLOW_SCOPE, PARAMETER_ALLOW_USERS, PARAMETER_ALLOW_PERIOD)) {
            setVisible(context, allow, !hideControls.value());
        }
    }

    private static void setVisible(@NotNull RichPageWidgetDependenciesContext context, @NotNull String name, boolean visible) {
        RichPageParameter parameter = context.parameter(name);
        parameter.visuals().setVisible(visible);
    }

    @Override
    public @NotNull String renderHtml(@NotNull RichPageWidgetRenderingContext richPageWidgetRenderingContext) {
        return new TimesheetReportWidgetRenderer(richPageWidgetRenderingContext).render();
    }

    @NotNull
    @Override
    public Iterable<String> getTags(@NotNull SharedContext context) {
        return new ImmutableStrictList<>(REPORTS);
    }
}
