package com.intechcore.polarion.extension.timesheet.widget;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.timesheet.manager.ReportStateStore;
import com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager;
import com.intechcore.polarion.extension.timesheet.model.ReportState;
import com.intechcore.polarion.extension.timesheet.model.Timesheet;
import com.intechcore.polarion.extension.timesheet.model.User;
import com.intechcore.polarion.extension.timesheet.rest.controller.TimesheetInternalController;
import com.intechcore.polarion.extension.timesheet.util.ScopeFactoryImpl;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.server.api.model.rp.widget.AbstractWidgetRenderer;
import com.polarion.alm.shared.api.Scope;
import com.polarion.alm.shared.api.model.ModelObjectReference;
import com.polarion.alm.shared.api.model.eo.EnumOption;
import com.polarion.alm.shared.api.model.rp.parameter.BooleanParameter;
import com.polarion.alm.shared.api.model.rp.parameter.CompositeParameter;
import com.polarion.alm.shared.api.model.rp.parameter.CustomEnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.DateParameter;
import com.polarion.alm.shared.api.model.rp.parameter.EnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.IntegerParameter;
import com.polarion.alm.shared.api.model.rp.parameter.ScopeParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetCommonContext;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.shared.api.utils.html.RichTextRenderTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
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
    private final boolean scopeLocked;
    private final boolean currentUser;
    private final List<String> userIds;
    private final boolean userLocked;
    private final int workingDayHours;
    private final String period;
    private final LocalDate periodFrom;
    private final LocalDate periodTo;
    private final boolean periodLocked;
    private final boolean hideControls;
    private final boolean printed;
    private final String stateKey;
    private final PrintedReportSources sources;

    // The targets that turn the page into a document. A PDF export or a print shows no iframe.
    private static final Set<RichTextRenderTarget> DOCUMENT_TARGETS = Set.of(RichTextRenderTarget.PDF_EXPORT,
            RichTextRenderTarget.COMPARE_PDF_EXPORT, RichTextRenderTarget.PRINT, RichTextRenderTarget.COMPARE_PRINT);

    /** What a printed report reads on the server: the work records, user names, the viewer and today. */
    interface PrintedReportSources {
        @NotNull Timesheet timesheet(@NotNull Scope scope, @NotNull List<String> userIds, @NotNull LocalDate start, @NotNull LocalDate end);

        @NotNull String userName(@NotNull String userId);

        @NotNull String scopeName(@NotNull Scope scope);

        @Nullable String currentUser();

        @NotNull LocalDate today();

        /** What the user last showed in this report on screen, or null. */
        @Nullable ReportState reportState(@NotNull String userId, @NotNull String stateKey);

        @NotNull Scope scope(@Nullable String scopePath);
    }

    public TimesheetReportWidgetRenderer(@NotNull RichPageWidgetCommonContext context) {
        this(context, new PolarionSources());
    }

    TimesheetReportWidgetRenderer(@NotNull RichPageWidgetCommonContext context, @NotNull PrintedReportSources sources) {
        super(context);
        this.sources = sources;
        printed = DOCUMENT_TARGETS.contains(context.target());

        ScopeParameter scopeParameter = context.parameter(TimesheetReportWidget.PARAMETER_SCOPE);
        scope = scopeParameter.scope();
        // Polarion builds every parameter from the definition, so a widget saved before one existed
        // gets its default: everything may be changed, the users of the settings, the current month,
        // the controls shown.
        scopeLocked = !isOn(context, TimesheetReportWidget.PARAMETER_ALLOW_SCOPE);
        userLocked = !isOn(context, TimesheetReportWidget.PARAMETER_ALLOW_USERS);
        periodLocked = !isOn(context, TimesheetReportWidget.PARAMETER_ALLOW_PERIOD);

        CustomEnumParameter usersMode = context.parameter(TimesheetReportWidget.PARAMETER_USERS_MODE);
        currentUser = TimesheetReportWidget.USERS_VIEWER.equals(usersMode.singleValue());
        EnumParameter userIdsParameter = context.parameter(TimesheetReportWidget.PARAMETER_USER_IDS);
        // The report opens on its viewer then, and the users of the settings would only mislead.
        userIds = currentUser ? List.of() : userIdsParameter.values().asList().stream()
                .map(EnumOption::id)
                .toList();

        CompositeParameter advancedCompositeParameter = context.parameter(TimesheetReportWidget.COMPOSITE_PARAMETER_ADVANCED);
        IntegerParameter workingDayHoursParameter = advancedCompositeParameter.get(TimesheetReportWidget.PARAMETER_WORKING_DAY_IN_HOURS);
        Integer workingDayHoursValue = workingDayHoursParameter.value();
        workingDayHours = (workingDayHoursValue != null && workingDayHoursValue > 0)
                ? workingDayHoursValue
                : TimesheetReportWidget.FULL_TIME_HOURS;

        CustomEnumParameter periodParameter = context.parameter(TimesheetReportWidget.PARAMETER_PERIOD);
        // No value when the period was cleared in the settings: the report opens on the current month.
        String periodValue = periodParameter.singleValue();
        period = periodValue != null ? periodValue : TimesheetReportWidget.PERIOD_CURRENT_MONTH;
        periodFrom = localDate(context.parameter(TimesheetReportWidget.PARAMETER_PERIOD_FROM));
        periodTo = localDate(context.parameter(TimesheetReportWidget.PARAMETER_PERIOD_TO));

        hideControls = isOn(context, TimesheetReportWidget.PARAMETER_HIDE_CONTROLS);

        stateKey = stateKey(context.getDisplayedReference(), settingsUrl());
    }

    /**
     * Names this report for the selection it keeps: the page and the widget settings, hashed. The API
     * gives a widget no id of its own, and the page renders the same settings on screen and in a PDF
     * export, so both find the same key. Two widgets with equal settings on one page share it.
     */
    static @NotNull String stateKey(@Nullable ModelObjectReference page, @NotNull String settings) {
        String source = (page == null ? "" : page.toPath()) + "\n" + settings;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform provides SHA-256.
            throw new IllegalStateException(e);
        }
    }

    private static boolean isOn(@NotNull RichPageWidgetCommonContext context, @NotNull String name) {
        BooleanParameter parameter = context.parameter(name);
        return parameter.value();
    }

    /**
     * The day of a date parameter, in the zone of the server: the one Polarion picked it in. A date
     * left untouched is today, a relative date with no shift, so there is always one.
     */
    private static @NotNull LocalDate localDate(@NotNull DateParameter parameter) {
        return parameter.value().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @Override
    protected void render(@NotNull final HtmlFragmentBuilder builder) {
        if (printed) {
            builder.html(printedReport());
            return;
        }
        String iframeId = "timesheet-report-" + UUID.randomUUID();

        HtmlTagBuilder iframe = builder.tag().byName("iframe");
        iframe.attributes()
                .id(iframeId)
                .byName("src", settingsUrl() + "&stateKey=" + stateKey)
                .byName("scrolling", "no")
                .width("100%")
                .style("border:0;width:100%;min-height:200px;");

        // The script of the resource, followed by the call that binds it to the iframe above. The
        // id is a UUID this method generated, so it needs no escaping.
        builder.tag().script().append().javaScript(
                HEIGHT_SYNC_SCRIPT + "%ntimesheetSyncIframeHeight('%s');".formatted(iframeId));
    }

    private static @NotNull String readHeightSyncScript() {
        return readScript(TimesheetReportWidgetRenderer.class.getResourceAsStream(HEIGHT_SYNC_RESOURCE), HEIGHT_SYNC_RESOURCE);
    }

    /**
     * Reads a script of the bundle. Both failures leave the widget without its listener, which is a
     * broken build rather than a state to recover from, so each one ends the call.
     *
     * <p>Package-private and taking the stream: the resource is opened by the caller, so a test can
     * hand this method the streams a jar cannot produce on demand.
     *
     * @param resource the open resource, or null when the bundle does not carry it
     * @param name the resource path, for the message
     * @return the text of the script
     */
    static @NotNull String readScript(@Nullable InputStream resource, @NotNull String name) {
        if (resource == null) {
            throw new IllegalStateException("Resource is missing from the bundle: " + name);
        }
        try (resource) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + name, e);
        }
    }

    /** What a report covers: the scope, the users and the period. */
    private record Selection(@NotNull Scope scope, @NotNull List<String> userIds, @NotNull LocalDate start, @NotNull LocalDate end) {
    }

    /**
     * The report for a document without iframes, read on the server. It shows what the viewer last
     * showed on screen, in the controls the widget lets them change, and the widget settings otherwise.
     */
    @NotNull String printedReport() {
        String viewer = sources.currentUser();
        Selection shown = fromSettings(viewer);
        ReportState state = viewer == null ? null : sources.reportState(viewer, stateKey);
        if (state != null) {
            shown = onScreen(shown, state);
        }
        Timesheet timesheet = shown.userIds().isEmpty()
                ? new Timesheet(shown.start().toString(), shown.end().toString(), List.of())
                : sources.timesheet(shown.scope(), shown.userIds(), shown.start(), shown.end());
        List<User> users = shown.userIds().stream().map(id -> new User(id, sources.userName(id))).toList();
        return new TimesheetReportHtml(sources.scopeName(shown.scope()), users, shown.start(), shown.end(), workingDayHours).render(timesheet);
    }

    /**
     * The report as it opens with the widget settings: the viewer when the widget shows the current
     * user or names nobody, the months counted from the day of the server.
     */
    private @NotNull Selection fromSettings(@Nullable String viewer) {
        List<String> ids = userIds;
        if (currentUser || userIds.isEmpty()) {
            ids = viewer == null ? List.of() : List.of(viewer);
        }
        LocalDate today = sources.today();
        LocalDate start;
        LocalDate end;
        if (TimesheetReportWidget.PERIOD_PREVIOUS_MONTH.equals(period)) {
            start = today.minusMonths(1).withDayOfMonth(1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        } else if (TimesheetReportWidget.PERIOD_CUSTOM.equals(period) && !periodFrom.isAfter(periodTo)) {
            start = periodFrom;
            end = periodTo;
        } else {
            // The current month, as the report falls back to for a custom period out of order.
            start = today.withDayOfMonth(1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        }
        return new Selection(scope, ids, start, end);
    }

    /** What the viewer showed on screen, in each control the widget lets them change. */
    private @NotNull Selection onScreen(@NotNull Selection settings, @NotNull ReportState state) {
        return new Selection(
                scopeLocked ? settings.scope() : sources.scope(state.getScopePath()),
                userLocked ? settings.userIds() : List.of(state.getUserIds().split(",")),
                periodLocked ? settings.start() : LocalDate.parse(state.getStartDate()),
                periodLocked ? settings.end() : LocalDate.parse(state.getEndDate()));
    }

    /** The sources of a running Polarion. The service is made on first use: a page view needs none. */
    private static final class PolarionSources implements PrintedReportSources {

        private PolarionService service;

        private PolarionService polarionService() {
            if (service == null) {
                service = new PolarionService();
            }
            return service;
        }

        @Override
        public @NotNull Timesheet timesheet(@NotNull Scope scope, @NotNull List<String> userIds, @NotNull LocalDate start, @NotNull LocalDate end) {
            return new TimesheetReportManager(polarionService()).getTimesheet(scope, userIds, start.toString(), end.toString());
        }

        @Override
        public @NotNull String userName(@NotNull String userId) {
            try {
                IUser user = polarionService().getProjectService().getUser(userId);
                return user == null || user.getName() == null ? userId : user.getName();
            } catch (RuntimeException e) {
                // A user Polarion cannot name is shown by the ID.
                return userId;
            }
        }

        /** The name the scope list shows (/internal/scopes): a project the user may not read by its ID. */
        @Override
        public @NotNull String scopeName(@NotNull Scope scope) {
            if (scope.isGlobal()) {
                return TimesheetInternalController.REPOSITORY_SCOPE_NAME;
            }
            String projectId = scope.projectId();
            if (projectId == null) {
                String path = scope.path();
                return path.substring(path.lastIndexOf('/') + 1);
            }
            try {
                IProject project = polarionService().getProjectService().getProject(projectId);
                return project.can().read() ? project.getName() : projectId;
            } catch (RuntimeException e) {
                return projectId;
            }
        }

        @Override
        public @Nullable String currentUser() {
            return polarionService().getSecurityService().getCurrentUser();
        }

        @Override
        public @NotNull LocalDate today() {
            return LocalDate.now();
        }

        @Override
        public @Nullable ReportState reportState(@NotNull String userId, @NotNull String stateKey) {
            return ReportStateStore.getInstance().find(userId, stateKey);
        }

        @Override
        public @NotNull Scope scope(@Nullable String scopePath) {
            return new ScopeFactoryImpl().fromPath(scopePath);
        }
    }

    private @NotNull String settingsUrl() {
        // Pass a canonical scope value that round-trips through ScopeFactoryImpl.fromPath and
        // matches the values offered by the /scopes endpoint: project id, "/" (root), or a group path.
        String scopeValue = scope.projectId() != null ? scope.projectId() : (scope.isGlobal() ? "/" : scope.path());

        return APP_URL + "?feature=report"
                + "&scope=" + enc(scopeValue)
                + (scopeLocked ? "&scopeLocked=true" : "")
                + "&userIds=" + enc(String.join(",", userIds))
                + (currentUser ? "&currentUser=true" : "")
                + (userLocked ? "&userLocked=true" : "")
                + "&workingDayInHours=" + workingDayHours
                + "&period=" + enc(period)
                + customPeriod()
                + (periodLocked ? "&periodLocked=true" : "")
                + (hideControls ? "&hideControls=true" : "");
    }

    /** The dates travel for a custom period only. The report checks their order. */
    private @NotNull String customPeriod() {
        if (!TimesheetReportWidget.PERIOD_CUSTOM.equals(period)) {
            return "";
        }
        return "&from=" + periodFrom + "&to=" + periodTo;
    }

    private static @NotNull String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

}
