package com.intechcore.polarion.extension.timesheet.widget;

import com.polarion.alm.server.api.model.rp.widget.AbstractWidgetRenderer;
import com.polarion.alm.shared.api.Scope;
import com.polarion.alm.shared.api.model.eo.EnumOption;
import com.polarion.alm.shared.api.model.rp.parameter.CompositeParameter;
import com.polarion.alm.shared.api.model.rp.parameter.EnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.IntegerParameter;
import com.polarion.alm.shared.api.model.rp.parameter.ScopeParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetCommonContext;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class TimesheetReportWidgetRenderer extends AbstractWidgetRenderer {

    private static final String APP_URL = "/polarion/timesheet-app/ui/app/index.html";

    private final Scope scope;
    private final List<String> userIds;
    private final int workingDayHours;

    public TimesheetReportWidgetRenderer(@NotNull RichPageWidgetCommonContext context) {
        super(context);

        ScopeParameter scopeParameter = context.parameter(TimesheetReportWidget.PARAMETER_SCOPE);
        scope = scopeParameter.scope();

        EnumParameter userIdsParameter = context.parameter(TimesheetReportWidget.PARAMETER_USER_IDS);
        userIds = userIdsParameter.values().asList().stream()
                .map(EnumOption::id)
                .toList();

        CompositeParameter advancedCompositeParameter = context.parameter(TimesheetReportWidget.COMPOSITE_PARAMETER_ADVANCED);
        IntegerParameter workingDayHoursParameter = advancedCompositeParameter.get(TimesheetReportWidget.PARAMETER_WORKING_DAY_IN_HOURS);
        Integer workingDayHoursValue = workingDayHoursParameter.value();
        workingDayHours = (workingDayHoursValue != null && workingDayHoursValue > 0)
                ? workingDayHoursValue
                : TimesheetReportWidget.FULL_TIME_HOURS;
    }

    @Override
    protected void render(@NotNull final HtmlFragmentBuilder builder) {
        String iframeId = "timesheet-report-" + UUID.randomUUID();

        HtmlTagBuilder iframe = builder.tag().byName("iframe");
        iframe.attributes()
                .id(iframeId)
                .byName("src", buildAppUrl())
                .byName("scrolling", "no")
                .width("100%")
                .style("border:0;width:100%;min-height:200px;");

        //language=JS
        builder.tag().script().append().javaScript("""
                (function () {
                    var frame = document.getElementById('%s');
                    window.addEventListener('message', function (event) {
                        if (frame && event.data && event.data.type === 'timesheet-app-height') {
                            frame.style.height = (event.data.height + 2) + 'px';
                        }
                    });
                })();""".formatted(iframeId));
    }

    private @NotNull String buildAppUrl() {
        // Pass a canonical scope value that round-trips through ScopeFactoryImpl.fromPath and
        // matches the values offered by the /scopes endpoint: project id, "/" (root), or a group path.
        String scopeValue = scope.projectId() != null ? scope.projectId() : (scope.isGlobal() ? "/" : scope.path());

        return APP_URL + "?feature=report"
                + "&scope=" + enc(scopeValue)
                + "&userIds=" + enc(String.join(",", userIds))
                + "&workingDayInHours=" + workingDayHours;
    }

    private static @NotNull String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

}
