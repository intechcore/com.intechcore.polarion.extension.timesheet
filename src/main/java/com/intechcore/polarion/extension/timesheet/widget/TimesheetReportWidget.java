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
    public static final String WORKING_DAY_IN_HOURS = "Working day in hours";
    public static final int FULL_TIME_HOURS = 8;

    public static final String PARAMETER_SCOPE = "scope";
    public static final String PARAMETER_USER_IDS = "userIds";
    public static final String COMPOSITE_PARAMETER_ADVANCED = "Advanced";
    public static final String PARAMETER_WORKING_DAY_IN_HOURS = "workingDayInHours";

    @Override
    public @NotNull String getIcon(@NotNull RichPageWidgetContext richPageWidgetContext) {
        return "/polarion/timesheet-app/ui/images/menu/30x30/_parent.svg";
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
        // The widget presets the defaults shown when the report opens (scope + users); the
        // reporting period defaults to the current month and is adjusted in the report itself.
        ScopeParameter scopeParameter = parameterFactory.scope(SCOPE).build();

        String currentUser = new PolarionService().getSecurityService().getCurrentUser();
        EnumParameter usersParameter = parameterFactory.enumeration(USERS, "@user")
                .dependencyTarget(true).dependencySource(true)
                .allowMultipleValues(true)
                .values(currentUser)
                .build();

        StrictMap<String, RichPageParameter> parameters = new StrictMapImpl<>();
        parameters.put(PARAMETER_SCOPE, scopeParameter);
        parameters.put(PARAMETER_USER_IDS, usersParameter);

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
