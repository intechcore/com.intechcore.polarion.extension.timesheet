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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class TimesheetReportWidgetRenderer extends AbstractWidgetRenderer {

    private static final String APP_URL = "/polarion/timesheet-app/ui/app/index.html";

    /**
     * The height listener of the widget. It is a file rather than a string in this class because a
     * Java test can assert the text of a script and never what it does; {@code
     * ui/test/widgetHeight.test.ts} loads this same file and drives it in a browser.
     */
    private static final String HEIGHT_SYNC_RESOURCE = "/js/widget-height.js";

    private static final String HEIGHT_SYNC_SCRIPT = readHeightSyncScript();

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

        // The script of the resource, followed by the call that binds it to the iframe above. The
        // id is a UUID this method generated, so it needs no escaping.
        builder.tag().script().append().javaScript(
                HEIGHT_SYNC_SCRIPT + "%ntimesheetSyncIframeHeight('%s');".formatted(iframeId));
    }

    private static @NotNull String readHeightSyncScript() {
        try (InputStream resource = TimesheetReportWidgetRenderer.class.getResourceAsStream(HEIGHT_SYNC_RESOURCE)) {
            if (resource == null) {
                throw new IllegalStateException("Resource is missing from the bundle: " + HEIGHT_SYNC_RESOURCE);
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + HEIGHT_SYNC_RESOURCE, e);
        }
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
