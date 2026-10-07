package com.intechcore.polarion.extension.timesheet.widget;

import com.polarion.alm.shared.api.Scope;
import com.polarion.alm.shared.api.model.eo.EnumOption;
import com.polarion.alm.shared.api.model.rp.parameter.BooleanParameter;
import com.polarion.alm.shared.api.model.rp.parameter.CompositeParameter;
import com.polarion.alm.shared.api.model.rp.parameter.CustomEnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.DateParameter;
import com.polarion.alm.shared.api.model.rp.parameter.EnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.IntegerParameter;
import com.polarion.alm.shared.api.model.rp.parameter.ScopeParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetCommonContext;
import com.polarion.alm.shared.api.utils.collections.StrictList;
import com.polarion.alm.shared.api.utils.html.HtmlAttributesBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The renderer turns the widget parameters into the URL of the report app. Everything the report
 * needs travels in that one query string, so each parameter is asserted where it lands.
 */
class TimesheetReportWidgetRendererTest {

    private RichPageWidgetCommonContext context;
    private Scope scope;
    private IntegerParameter workingDayHours;

    private HtmlFragmentBuilder builder;
    private HtmlAttributesBuilder attributes;
    private HtmlContentBuilder scriptContent;

    private static EnumOption option(String id) {
        EnumOption enumOption = mock(EnumOption.class);
        when(enumOption.id()).thenReturn(id);
        return enumOption;
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        context = mock(RichPageWidgetCommonContext.class);
        // The page shown in Polarion; a PDF export or a print sets another target.
        when(context.target()).thenReturn(com.polarion.alm.shared.api.utils.html.RichTextRenderTarget.RP_VIEW);

        scope = mock(Scope.class);
        ScopeParameter scopeParameter = mock(ScopeParameter.class);
        when(scopeParameter.scope()).thenReturn(scope);
        when(context.<ScopeParameter>parameter(TimesheetReportWidget.PARAMETER_SCOPE)).thenReturn(scopeParameter);

        // The options are built before the stubbing below: a mock created inside thenReturn(...)
        // opens a second stubbing while the first one is still open.
        List<EnumOption> options = List.of(option("aSeller"), option("mTest"));
        StrictList<EnumOption> values = mock(StrictList.class);
        when(values.asList()).thenReturn(options);
        EnumParameter userIdsParameter = mock(EnumParameter.class);
        when(userIdsParameter.values()).thenReturn(values);
        when(context.<EnumParameter>parameter(TimesheetReportWidget.PARAMETER_USER_IDS)).thenReturn(userIdsParameter);

        workingDayHours = mock(IntegerParameter.class);
        CompositeParameter advanced = mock(CompositeParameter.class);
        when(advanced.<IntegerParameter>get(TimesheetReportWidget.PARAMETER_WORKING_DAY_IN_HOURS)).thenReturn(workingDayHours);
        when(context.<CompositeParameter>parameter(TimesheetReportWidget.COMPOSITE_PARAMETER_ADVANCED)).thenReturn(advanced);

        // Polarion builds every parameter from the definition: unset, each one has its default. Off,
        // no period and no user mode chosen are a mock's false and null; an untouched date is today;
        // every "Allow changing" is on.
        BooleanParameter off = mock(BooleanParameter.class);
        BooleanParameter on = mock(BooleanParameter.class);
        when(on.value()).thenReturn(true);
        CustomEnumParameter noPeriod = mock(CustomEnumParameter.class);
        CustomEnumParameter noUsersMode = mock(CustomEnumParameter.class);
        DateParameter noDate = dateParameter(LocalDate.now());
        when(context.<CustomEnumParameter>parameter(TimesheetReportWidget.PARAMETER_USERS_MODE)).thenReturn(noUsersMode);
        when(context.<BooleanParameter>parameter(TimesheetReportWidget.PARAMETER_HIDE_CONTROLS)).thenReturn(off);
        when(context.<BooleanParameter>parameter(TimesheetReportWidget.PARAMETER_ALLOW_SCOPE)).thenReturn(on);
        when(context.<BooleanParameter>parameter(TimesheetReportWidget.PARAMETER_ALLOW_USERS)).thenReturn(on);
        when(context.<BooleanParameter>parameter(TimesheetReportWidget.PARAMETER_ALLOW_PERIOD)).thenReturn(on);
        when(context.<CustomEnumParameter>parameter(TimesheetReportWidget.PARAMETER_PERIOD)).thenReturn(noPeriod);
        when(context.<DateParameter>parameter(TimesheetReportWidget.PARAMETER_PERIOD_FROM)).thenReturn(noDate);
        when(context.<DateParameter>parameter(TimesheetReportWidget.PARAMETER_PERIOD_TO)).thenReturn(noDate);

        attributes = mock(HtmlAttributesBuilder.class, RETURNS_SELF);
        HtmlTagBuilder iframe = mock(HtmlTagBuilder.class);
        when(iframe.attributes()).thenReturn(attributes);

        scriptContent = mock(HtmlContentBuilder.class);
        HtmlTagBuilder script = mock(HtmlTagBuilder.class);
        when(script.append()).thenReturn(scriptContent);

        HtmlTagSelector<HtmlTagBuilder> tags = mock(HtmlTagSelector.class);
        when(tags.byName("iframe")).thenReturn(iframe);
        when(tags.script()).thenReturn(script);

        builder = mock(HtmlFragmentBuilder.class);
        when(builder.tag()).thenReturn(tags);
    }

    /** The URL of the report, without the key that ends it: the settings it carries. */
    private String renderedUrl() {
        String url = renderedUrlWithKey();
        return url.substring(0, url.indexOf("&stateKey="));
    }

    private String renderedUrlWithKey() {
        new TimesheetReportWidgetRenderer(context).render(builder);

        ArgumentCaptor<String> src = ArgumentCaptor.forClass(String.class);
        verify(attributes, org.mockito.Mockito.atLeastOnce()).byName(eq("src"), src.capture());
        return src.getValue();
    }

    private void flag(String name, boolean value) {
        BooleanParameter parameter = mock(BooleanParameter.class);
        when(parameter.value()).thenReturn(value);
        when(context.<BooleanParameter>parameter(name)).thenReturn(parameter);
    }

    /** Everything may be changed by default: no control of the report is locked. */
    @Test
    void locksNothingByDefault() {
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).doesNotContain("Locked");
    }

    @Test
    void locksTheScopeWhenChangingItIsNotAllowed() {
        flag(TimesheetReportWidget.PARAMETER_ALLOW_SCOPE, false);
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).contains("&scope=elibrary&scopeLocked=true&").doesNotContain("userLocked");
    }

    @Test
    void locksTheUsersWhenChangingThemIsNotAllowed() {
        flag(TimesheetReportWidget.PARAMETER_ALLOW_USERS, false);
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).contains("&userIds=aSeller%2CmTest&userLocked=true&").doesNotContain("scopeLocked");
    }

    @Test
    void locksThePeriodWhenChangingItIsNotAllowed() {
        flag(TimesheetReportWidget.PARAMETER_ALLOW_PERIOD, false);
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).endsWith("&period=current-month&periodLocked=true");
    }

    private void showTheViewer() {
        CustomEnumParameter usersMode = mock(CustomEnumParameter.class);
        when(usersMode.singleValue()).thenReturn(TimesheetReportWidget.USERS_VIEWER);
        when(context.<CustomEnumParameter>parameter(TimesheetReportWidget.PARAMETER_USERS_MODE)).thenReturn(usersMode);
    }

    /** "Viewer of the page" opens the report on its viewer: the users of the settings do not travel. */
    @Test
    void opensOnTheViewerWithoutLockingThem() {
        showTheViewer();
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).contains("&userIds=&currentUser=true&").doesNotContain("userLocked");
    }

    /** The two combine: every viewer sees their own hours, and only those. */
    @Test
    void locksTheViewerWhenCurrentUserMayNotBeChanged() {
        showTheViewer();
        flag(TimesheetReportWidget.PARAMETER_ALLOW_USERS, false);
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).contains("&userIds=&currentUser=true&userLocked=true&");
    }

    /** "Current user" off, its default: the users of the settings travel. */
    @Test
    void keepsTheUsersOfTheSettingsWhenCurrentUserIsOff() {
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).contains("&userIds=aSeller%2CmTest&").doesNotContain("currentUser");
    }

    private void period(String value, LocalDate from, LocalDate to) {
        CustomEnumParameter periodParameter = mock(CustomEnumParameter.class);
        when(periodParameter.singleValue()).thenReturn(value);
        // Built before the stubbing: a mock created inside thenReturn(...) opens a second stubbing.
        DateParameter fromParameter = dateParameter(from);
        DateParameter toParameter = dateParameter(to);
        when(context.<CustomEnumParameter>parameter(TimesheetReportWidget.PARAMETER_PERIOD)).thenReturn(periodParameter);
        when(context.<DateParameter>parameter(TimesheetReportWidget.PARAMETER_PERIOD_FROM)).thenReturn(fromParameter);
        when(context.<DateParameter>parameter(TimesheetReportWidget.PARAMETER_PERIOD_TO)).thenReturn(toParameter);
    }

    private static DateParameter dateParameter(LocalDate day) {
        DateParameter parameter = mock(DateParameter.class);
        when(parameter.value()).thenReturn(Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        return parameter;
    }

    /** A period cleared in the settings has no value: the report opens on the current month. */
    @Test
    void passesTheCurrentMonthWhenNoPeriodIsChosen() {
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).endsWith("&period=current-month");
    }

    /** The months are counted by the browser of the viewer: the widget passes only which one. */
    @Test
    void passesAMonthPeriodWithoutDates() {
        when(scope.projectId()).thenReturn("elibrary");
        period(TimesheetReportWidget.PERIOD_PREVIOUS_MONTH, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(renderedUrl()).endsWith("&period=previous-month");
    }

    @Test
    void passesTheDatesOfACustomPeriod() {
        when(scope.projectId()).thenReturn("elibrary");
        period(TimesheetReportWidget.PERIOD_CUSTOM, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(renderedUrl()).endsWith("&period=custom&from=2026-09-01&to=2026-09-30");
    }

    @Test
    void hidesTheControlsWhenTheWidgetAsks() {
        flag(TimesheetReportWidget.PARAMETER_HIDE_CONTROLS, true);
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(renderedUrl()).endsWith("&period=current-month&hideControls=true");
    }

    @Test
    void passesTheProjectScopeAsItsId() {
        when(scope.projectId()).thenReturn("elibrary");
        when(workingDayHours.value()).thenReturn(8);

        assertThat(renderedUrl())
                .startsWith("/polarion/timesheet-app/ui/app/index.html?feature=report")
                .contains("&scope=elibrary")
                .contains("&userIds=aSeller%2CmTest")
                .contains("&workingDayInHours=8&period=current-month");
    }

    /** The global scope travels as "/", which is what the scopes endpoint offers for the root. */
    @Test
    void passesTheGlobalScopeAsASlash() {
        when(scope.projectId()).thenReturn(null);
        when(scope.isGlobal()).thenReturn(true);

        assertThat(renderedUrl()).contains("&scope=%2F");
    }

    @Test
    void passesAGroupScopeAsItsPath() {
        when(scope.projectId()).thenReturn(null);
        when(scope.isGlobal()).thenReturn(false);
        when(scope.path()).thenReturn("/drafts");

        assertThat(renderedUrl()).contains("&scope=%2Fdrafts");
    }

    /** A scope that answers nothing at all still has to produce a URL, with an empty scope. */
    @Test
    void passesAnEmptyScopeWhenThereIsNone() {
        when(scope.projectId()).thenReturn(null);
        when(scope.isGlobal()).thenReturn(false);
        when(scope.path()).thenReturn(null);

        assertThat(renderedUrl()).contains("&scope=&userIds=");
    }

    @Test
    void encodesAScopeThatNeedsIt() {
        when(scope.projectId()).thenReturn("a b&c");

        assertThat(renderedUrl()).contains("&scope=a+b%26c");
    }

    @Test
    void fallsBackToTheFullWorkingDay() {
        when(scope.projectId()).thenReturn("elibrary");
        when(workingDayHours.value()).thenReturn(null);

        assertThat(renderedUrl()).contains("&workingDayInHours=" + TimesheetReportWidget.FULL_TIME_HOURS + "&");
    }

    @Test
    void rejectsAWorkingDayOfZeroOrLess() {
        when(scope.projectId()).thenReturn("elibrary");
        when(workingDayHours.value()).thenReturn(0);

        assertThat(renderedUrl()).contains("&workingDayInHours=" + TimesheetReportWidget.FULL_TIME_HOURS + "&");
    }

    @Test
    void keepsAWorkingDayTheUserSet() {
        when(scope.projectId()).thenReturn("elibrary");
        when(workingDayHours.value()).thenReturn(6);

        assertThat(renderedUrl()).contains("&workingDayInHours=6&");
    }

    /**
     * The script resizes the iframe from the height the app posts, and it is bound to the frame by
     * the id the same render call wrote. A mismatch leaves the report clipped at its minimum height.
     *
     * <p>What the listener does with a message is asserted where it can run, in
     * {@code ui/test/widgetHeight.test.ts}. This test covers the seam: the resource is served whole,
     * and the call names this iframe.
     */
    @Test
    void theScriptAddressesTheIframeItJustCreated() {
        when(scope.projectId()).thenReturn("elibrary");
        new TimesheetReportWidgetRenderer(context).render(builder);

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(attributes).id(id.capture());
        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(scriptContent).javaScript(script.capture());

        assertThat(id.getValue()).startsWith("timesheet-report-");
        assertThat(script.getValue())
                .contains(readResource("/js/widget-height.js"))
                .endsWith("timesheetSyncIframeHeight('" + id.getValue() + "');");
    }

    // --- Reading the script out of the bundle ---

    @Test
    void readScript_returnsTheText() {
        InputStream resource = new ByteArrayInputStream("function f() {}".getBytes(StandardCharsets.UTF_8));

        assertThat(TimesheetReportWidgetRenderer.readScript(resource, "/js/any.js")).isEqualTo("function f() {}");
    }

    /** A bundle without the script leaves the widget without its listener, so the call ends there. */
    @Test
    void readScript_refusesAResourceTheBundleDoesNotCarry() {
        assertThatThrownBy(() -> TimesheetReportWidgetRenderer.readScript(null, "/js/absent.js"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Resource is missing from the bundle: /js/absent.js");
    }

    @Test
    void readScript_reportsAResourceItCannotRead() throws IOException {
        InputStream failing = mock(InputStream.class);
        when(failing.readAllBytes()).thenThrow(new IOException("no"));

        assertThatThrownBy(() -> TimesheetReportWidgetRenderer.readScript(failing, "/js/broken.js"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot read /js/broken.js")
                .cause().isInstanceOf(IOException.class);
    }

    private static String readResource(String path) {
        try (InputStream resource = TimesheetReportWidgetRendererTest.class.getResourceAsStream(path)) {
            assertThat(resource).as("resource %s", path).isNotNull();
            return new String(Objects.requireNonNull(resource).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + path, e);
        }
    }

    @Test
    void theIframeCarriesNoBorderAndNoScrollbars() {
        when(scope.projectId()).thenReturn("elibrary");
        new TimesheetReportWidgetRenderer(context).render(builder);

        verify(attributes).byName("scrolling", "no");
        verify(attributes).width("100%");
        verify(attributes).style(anyString());
    }

    /** A source that answers from memory: the records of a test, names by ID, a fixed viewer and day. */
    private static final class Sources implements TimesheetReportWidgetRenderer.PrintedReportSources {
        private final String viewer;
        private final java.util.List<java.util.List<Object>> calls = new java.util.ArrayList<>();
        private final java.util.Map<String, com.intechcore.polarion.extension.timesheet.model.ReportState> states = new java.util.HashMap<>();

        Sources(String viewer) {
            this.viewer = viewer;
        }

        @Override
        public com.intechcore.polarion.extension.timesheet.model.Timesheet timesheet(Scope scope, List<String> userIds, LocalDate start, LocalDate end) {
            calls.add(List.of(userIds, start, end));
            com.intechcore.polarion.extension.timesheet.model.Timesheet timesheet =
                    new com.intechcore.polarion.extension.timesheet.model.Timesheet(start.toString(), end.toString(), new java.util.ArrayList<>());
            timesheet.addWorkRecord(new com.intechcore.polarion.extension.timesheet.model.WorkRecord(start.toString(),
                    new com.intechcore.polarion.extension.timesheet.model.WorkItem(
                            new com.intechcore.polarion.extension.timesheet.model.Project("elibrary", "E-Library"), "EL-1", "Spec", null, null),
                    new com.intechcore.polarion.extension.timesheet.model.User(userIds.get(0), "ignored"), 8));
            return timesheet;
        }

        @Override
        public String userName(String userId) {
            return "Name of " + userId;
        }

        @Override
        public com.intechcore.polarion.extension.timesheet.model.ReportState reportState(String userId, String stateKey) {
            return states.get(userId + "/" + stateKey);
        }

        @Override
        public Scope scope(String scopePath) {
            Scope chosen = mock(Scope.class);
            when(chosen.projectId()).thenReturn(scopePath);
            return chosen;
        }

        @Override
        public String scopeName(Scope scope) {
            return "Scope of " + scope.projectId();
        }

        @Override
        public String currentUser() {
            return viewer;
        }

        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 10, 7);
        }
    }

    private String printed(Sources sources) {
        when(context.target()).thenReturn(com.polarion.alm.shared.api.utils.html.RichTextRenderTarget.PDF_EXPORT);
        new TimesheetReportWidgetRenderer(context, sources).render(builder);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(builder).html(html.capture());
        verify(builder, org.mockito.Mockito.never()).tag();
        return html.getValue();
    }

    /** A PDF export or a print shows the report itself: an iframe has no content in a document. */
    @Test
    void writesTheReportOfTheUsersOfTheSettingsForAPdfExport() {
        Sources sources = new Sources("viewer");

        String html = printed(sources);

        assertThat(sources.calls).containsExactly(List.of(List.of("aSeller", "mTest"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertThat(html).contains("Name of aSeller - total: 8 h").contains("Name of mTest - total: 0 h").contains("EL-1 - Spec");
    }

    @Test
    void printsTheViewerForTheCurrentUserAndThePreviousMonth() {
        showTheViewer();
        period(TimesheetReportWidget.PERIOD_PREVIOUS_MONTH, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        Sources sources = new Sources("viewer");

        assertThat(printed(sources)).contains("Name of viewer - total: 8 h");
        assertThat(sources.calls).containsExactly(List.of(List.of("viewer"), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void printsACustomPeriodAndFallsBackToTheMonthForOneOutOfOrder() {
        period(TimesheetReportWidget.PERIOD_CUSTOM, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2));
        Sources sources = new Sources("viewer");
        printed(sources);
        assertThat(sources.calls.get(0)).containsExactly(List.of("aSeller", "mTest"), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2));

        period(TimesheetReportWidget.PERIOD_CUSTOM, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 9, 28));
        assertThat(new TimesheetReportWidgetRenderer(context, sources).printedReport()).contains("total");
        assertThat(sources.calls.get(1)).containsExactly(List.of("aSeller", "mTest"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    /** A widget with the current user, read by nobody signed in, has nobody to show and asks for nothing. */
    @Test
    void printsNoUserWithoutAViewer() {
        showTheViewer();
        Sources sources = new Sources(null);

        assertThat(printed(sources)).contains("No users selected");
        assertThat(sources.calls).isEmpty();
    }

    /** In Polarion the report reads the records through the manager, and names each user, or shows the ID. */
    @Test
    void readsThePrintedReportFromPolarion() {
        when(context.target()).thenReturn(com.polarion.alm.shared.api.utils.html.RichTextRenderTarget.PRINT);
        com.polarion.alm.projects.model.IUser named = mock(com.polarion.alm.projects.model.IUser.class);
        when(named.getName()).thenReturn("Anna Seller");
        try (org.mockito.MockedConstruction<ch.sbb.polarion.extension.generic.service.PolarionService> services =
                     org.mockito.Mockito.mockConstruction(ch.sbb.polarion.extension.generic.service.PolarionService.class,
                             org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Mockito.RETURNS_DEEP_STUBS),
                             (service, construction) -> {
                                 when(service.getProjectService().getUser("aSeller")).thenReturn(named);
                                 when(service.getProjectService().getUser("mTest")).thenThrow(new IllegalStateException("no transaction"));
                                 when(service.getProjectService().getProject("elibrary").can().read()).thenReturn(true);
                                 when(service.getProjectService().getProject("elibrary").getName()).thenReturn("E-Library");
                             });
             org.mockito.MockedConstruction<com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager> managers =
                     org.mockito.Mockito.mockConstruction(com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager.class,
                             (manager, construction) -> when(manager.getTimesheet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                                     anyString(), anyString())).thenReturn(new com.intechcore.polarion.extension.timesheet.model.Timesheet("a", "b", List.of())))) {

            when(scope.projectId()).thenReturn("elibrary");
            String html = new TimesheetReportWidgetRenderer(context).printedReport();

            assertThat(html).contains("Scope: E-Library").contains("Anna Seller - total: 0 h").contains("mTest - total: 0 h");
            assertThat(managers.constructed()).hasSize(1);
            // One service for the whole report.
            assertThat(services.constructed()).hasSize(1);
        }
    }

    /** The scope line names the scope as the scope list does, and a project the user may not read by its ID. */
    @Test
    void namesTheScopeOfThePrintedReport() {
        when(context.target()).thenReturn(com.polarion.alm.shared.api.utils.html.RichTextRenderTarget.PRINT);
        try (org.mockito.MockedConstruction<ch.sbb.polarion.extension.generic.service.PolarionService> ignored =
                     org.mockito.Mockito.mockConstruction(ch.sbb.polarion.extension.generic.service.PolarionService.class,
                             org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Mockito.RETURNS_DEEP_STUBS),
                             (service, construction) -> {
                                 when(service.getProjectService().getProject("secret").can().read()).thenReturn(false);
                                 when(service.getProjectService().getProject("broken")).thenThrow(new IllegalStateException("no transaction"));
                             });
             org.mockito.MockedConstruction<com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager> ignoredManagers =
                     org.mockito.Mockito.mockConstruction(com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager.class,
                             (manager, construction) -> when(manager.getTimesheet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                                     anyString(), anyString())).thenReturn(new com.intechcore.polarion.extension.timesheet.model.Timesheet("a", "b", List.of())))) {

            when(scope.isGlobal()).thenReturn(true);
            assertThat(new TimesheetReportWidgetRenderer(context).printedReport()).contains("Scope: Repository (all projects)");

            when(scope.isGlobal()).thenReturn(false);
            when(scope.path()).thenReturn("/Demo Projects");
            assertThat(new TimesheetReportWidgetRenderer(context).printedReport()).contains("Scope: Demo Projects");

            when(scope.projectId()).thenReturn("secret");
            assertThat(new TimesheetReportWidgetRenderer(context).printedReport()).contains("Scope: secret");

            when(scope.projectId()).thenReturn("broken");
            assertThat(new TimesheetReportWidgetRenderer(context).printedReport()).contains("Scope: broken");
        }
    }

    // --- What the viewer showed on screen, for a PDF export ---

    private String stateKeyOfThePage() {
        String url = renderedUrlWithKey();
        return url.substring(url.indexOf("&stateKey=") + "&stateKey=".length());
    }

    /** The page and the settings name the report: the same on every render, different for another page or setting. */
    @Test
    void namesTheReportByThePageAndItsSettings() {
        com.polarion.alm.shared.api.model.ModelObjectReference page = mock(com.polarion.alm.shared.api.model.ModelObjectReference.class);
        when(page.toPath()).thenReturn("elibrary/custom/Time Sheet Report");
        com.polarion.alm.shared.api.model.ModelObjectReference other = mock(com.polarion.alm.shared.api.model.ModelObjectReference.class);
        when(other.toPath()).thenReturn("elibrary/custom/Other");

        String key = TimesheetReportWidgetRenderer.stateKey(page, "?scope=elibrary");
        assertThat(key).matches("[0-9a-f]{64}").isEqualTo(TimesheetReportWidgetRenderer.stateKey(page, "?scope=elibrary"));
        assertThat(TimesheetReportWidgetRenderer.stateKey(other, "?scope=elibrary")).isNotEqualTo(key);
        assertThat(TimesheetReportWidgetRenderer.stateKey(page, "?scope=library")).isNotEqualTo(key);
        assertThat(TimesheetReportWidgetRenderer.stateKey(null, "?scope=elibrary")).isNotEqualTo(key);
    }

    @Test
    void passesItsKeyToTheReport() {
        when(scope.projectId()).thenReturn("elibrary");

        assertThat(stateKeyOfThePage()).matches("[0-9a-f]{64}");
    }

    /** The export shows what the viewer last showed on screen: scope, users and period. */
    @Test
    void printsWhatTheViewerShowedOnScreen() {
        when(scope.projectId()).thenReturn("elibrary");
        Sources sources = new Sources("viewer");
        sources.states.put("viewer/" + stateKeyOfThePage(),
                new com.intechcore.polarion.extension.timesheet.model.ReportState("library", "mTest", "2026-06-01", "2026-08-31"));

        String html = new TimesheetReportWidgetRenderer(context, sources).printedReport();

        assertThat(html).contains("Scope: Scope of library").contains("Period: 2026-06-01 - 2026-08-31").contains("Name of mTest");
        assertThat(sources.calls).containsExactly(List.of(List.of("mTest"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)));
    }

    /** A control the widget locks keeps its setting, whatever the screen showed. */
    @Test
    void keepsTheLockedControlsAtTheirSettings() {
        when(scope.projectId()).thenReturn("elibrary");
        flag(TimesheetReportWidget.PARAMETER_ALLOW_SCOPE, false);
        flag(TimesheetReportWidget.PARAMETER_ALLOW_USERS, false);
        flag(TimesheetReportWidget.PARAMETER_ALLOW_PERIOD, false);
        Sources sources = new Sources("viewer");
        sources.states.put("viewer/" + stateKeyOfThePage(),
                new com.intechcore.polarion.extension.timesheet.model.ReportState("library", "mTest", "2026-06-01", "2026-08-31"));

        String html = new TimesheetReportWidgetRenderer(context, sources).printedReport();

        assertThat(html).contains("Scope: Scope of elibrary").contains("Period: 2026-10-01 - 2026-10-31");
        assertThat(sources.calls).containsExactly(List.of(List.of("aSeller", "mTest"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    /** Another viewer's screen is not this viewer's export. */
    @Test
    void readsOnlyTheSelectionOfTheViewer() {
        when(scope.projectId()).thenReturn("elibrary");
        Sources sources = new Sources("viewer");
        sources.states.put("someoneElse/" + stateKeyOfThePage(),
                new com.intechcore.polarion.extension.timesheet.model.ReportState("library", "mTest", "2026-06-01", "2026-08-31"));

        new TimesheetReportWidgetRenderer(context, sources).printedReport();

        assertThat(sources.calls).containsExactly(List.of(List.of("aSeller", "mTest"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    /** Every Java platform provides SHA-256; a platform without it is a broken one, said as such. */
    @Test
    void failsLoudlyWithoutSha256() {
        try (org.mockito.MockedStatic<java.security.MessageDigest> digests = org.mockito.Mockito.mockStatic(java.security.MessageDigest.class)) {
            digests.when(() -> java.security.MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new java.security.NoSuchAlgorithmException("SHA-256"));

            assertThatThrownBy(() -> TimesheetReportWidgetRenderer.stateKey(null, "?scope=elibrary"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(java.security.NoSuchAlgorithmException.class);
        }
    }

    /** In Polarion the export reads the viewer's selection from the store, scope included; a user without a name shows the ID. */
    @Test
    void readsTheSelectionOnScreenFromPolarion() {
        when(scope.projectId()).thenReturn("elibrary");
        String key = stateKeyOfThePage();
        when(context.target()).thenReturn(com.polarion.alm.shared.api.utils.html.RichTextRenderTarget.PRINT);
        com.intechcore.polarion.extension.timesheet.manager.ReportStateStore.getInstance().save("viewer", key,
                new com.intechcore.polarion.extension.timesheet.model.ReportState("library", "nameless", "2026-06-01", "2026-06-30"));
        com.polarion.alm.projects.model.IUser nameless = mock(com.polarion.alm.projects.model.IUser.class);
        try (org.mockito.MockedConstruction<ch.sbb.polarion.extension.generic.service.PolarionService> ignored =
                     org.mockito.Mockito.mockConstruction(ch.sbb.polarion.extension.generic.service.PolarionService.class,
                             org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Mockito.RETURNS_DEEP_STUBS),
                             (service, construction) -> {
                                 when(service.getSecurityService().getCurrentUser()).thenReturn("viewer");
                                 when(service.getProjectService().getUser("nameless")).thenReturn(nameless);
                                 when(service.getProjectService().getProject("library").can().read()).thenReturn(true);
                                 when(service.getProjectService().getProject("library").getName()).thenReturn("Document Library");
                             });
             org.mockito.MockedConstruction<com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager> ignoredManagers =
                     org.mockito.Mockito.mockConstruction(com.intechcore.polarion.extension.timesheet.manager.TimesheetReportManager.class,
                             (manager, construction) -> when(manager.getTimesheet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                                     anyString(), anyString())).thenReturn(new com.intechcore.polarion.extension.timesheet.model.Timesheet("a", "b", List.of())))) {

            String html = new TimesheetReportWidgetRenderer(context).printedReport();

            assertThat(html).contains("Scope: Document Library").contains("Period: 2026-06-01 - 2026-06-30").contains("nameless - total: 0 h");
        }
    }

    /** A widget that names nobody shows its viewer, as the report on screen does. */
    @Test
    void printsTheViewerWhenTheWidgetNamesNobody() {
        @SuppressWarnings("unchecked")
        StrictList<EnumOption> none = mock(StrictList.class);
        when(none.asList()).thenReturn(List.of());
        EnumParameter nobody = mock(EnumParameter.class);
        when(nobody.values()).thenReturn(none);
        when(context.<EnumParameter>parameter(TimesheetReportWidget.PARAMETER_USER_IDS)).thenReturn(nobody);
        Sources sources = new Sources("viewer");

        assertThat(printed(sources)).contains("Name of viewer - total: 8 h");
    }
}
