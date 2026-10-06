package com.intechcore.polarion.extension.timesheet.widget;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.model.rp.parameter.*;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidget;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetContext;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetRenderingContext;
import com.polarion.alm.shared.api.utils.collections.ImmutableStrictList;
import com.polarion.alm.shared.api.utils.collections.ReadOnlyStrictMap;
import com.polarion.alm.shared.api.utils.collections.StrictMap;
import com.polarion.alm.shared.api.utils.collections.StrictMapImpl;
import org.jetbrains.annotations.NotNull;

public class TimesheetReportWidget extends RichPageWidget {

    public static final String REPORTS = "Reports";
    public static final String SCOPE = "Scope";
    public static final String USERS = "Users";
    public static final String CURRENT_USER = "Current user";
    public static final String PERIOD = "Period";
    public static final String PERIOD_FROM = "From (custom period)";
    public static final String PERIOD_TO = "To (custom period)";
    public static final String HIDE_CONTROLS = "Hide controls";
    public static final String WORKING_DAY_IN_HOURS = "Working day in hours";
    public static final int FULL_TIME_HOURS = 8;

    public static final String PARAMETER_SCOPE = "scope";
    public static final String PARAMETER_USER_IDS = "userIds";
    public static final String PARAMETER_CURRENT_USER = "currentUser";
    public static final String PARAMETER_PERIOD = "period";
    public static final String PARAMETER_PERIOD_FROM = "periodFrom";
    public static final String PARAMETER_PERIOD_TO = "periodTo";
    public static final String PARAMETER_HIDE_CONTROLS = "hideControls";

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
        // Shows every viewer their own hours, whoever set the widget up. It overrides the users above.
        BooleanParameter currentUserParameter = parameterFactory.bool(CURRENT_USER).value(false).build();

        parameters.put(PARAMETER_SCOPE, scopeParameter);
        parameters.put(PARAMETER_CURRENT_USER, currentUserParameter);
        parameters.put(PARAMETER_USER_IDS, usersParameter);

        // The months are counted by the browser of the viewer, when the report opens, so the widget
        // passes only which one. The dates are read for a custom period only.
        CustomEnumParameter periodParameter = parameterFactory.customEnum(PERIOD)
                .addEnumItem(PERIOD_CURRENT_MONTH, "Current month")
                .addEnumItem(PERIOD_PREVIOUS_MONTH, "Previous month")
                .addEnumItem(PERIOD_CUSTOM, "Custom")
                .singleValue(PERIOD_CURRENT_MONTH)
                .build();
        parameters.put(PARAMETER_PERIOD, periodParameter);
        parameters.put(PARAMETER_PERIOD_FROM, parameterFactory.date(PERIOD_FROM).build());
        parameters.put(PARAMETER_PERIOD_TO, parameterFactory.date(PERIOD_TO).build());

        // The page shows the tables only: no title, no scope, users, period or export.
        parameters.put(PARAMETER_HIDE_CONTROLS, parameterFactory.bool(HIDE_CONTROLS).value(false).build());

        IntegerParameter workingDayInHoursParameter = parameterFactory.integer(WORKING_DAY_IN_HOURS).value(FULL_TIME_HOURS).build();
        CompositeParameter advancedCompositeParameter = parameterFactory.composite(COMPOSITE_PARAMETER_ADVANCED)
                .collapsedByDefault(true)
                .add(PARAMETER_WORKING_DAY_IN_HOURS, workingDayInHoursParameter)
                .build();
        parameters.put(COMPOSITE_PARAMETER_ADVANCED, advancedCompositeParameter);

        return parameters;
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
