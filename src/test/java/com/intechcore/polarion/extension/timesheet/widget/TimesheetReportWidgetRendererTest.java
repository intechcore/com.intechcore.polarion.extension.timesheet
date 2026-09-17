package com.intechcore.polarion.extension.timesheet.widget;

import com.polarion.alm.shared.api.Scope;
import com.polarion.alm.shared.api.model.eo.EnumOption;
import com.polarion.alm.shared.api.model.rp.parameter.CompositeParameter;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    private String renderedUrl() {
        new TimesheetReportWidgetRenderer(context).render(builder);

        ArgumentCaptor<String> src = ArgumentCaptor.forClass(String.class);
        verify(attributes).byName(eq("src"), src.capture());
        return src.getValue();
    }

    @Test
    void passesTheProjectScopeAsItsId() {
        when(scope.projectId()).thenReturn("elibrary");
        when(workingDayHours.value()).thenReturn(8);

        assertThat(renderedUrl())
                .startsWith("/polarion/timesheet-app/ui/app/index.html?feature=report")
                .contains("&scope=elibrary")
                .contains("&userIds=aSeller%2CmTest")
                .endsWith("&workingDayInHours=8");
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

        assertThat(renderedUrl()).endsWith("&workingDayInHours=" + TimesheetReportWidget.FULL_TIME_HOURS);
    }

    @Test
    void rejectsAWorkingDayOfZeroOrLess() {
        when(scope.projectId()).thenReturn("elibrary");
        when(workingDayHours.value()).thenReturn(0);

        assertThat(renderedUrl()).endsWith("&workingDayInHours=" + TimesheetReportWidget.FULL_TIME_HOURS);
    }

    @Test
    void keepsAWorkingDayTheUserSet() {
        when(scope.projectId()).thenReturn("elibrary");
        when(workingDayHours.value()).thenReturn(6);

        assertThat(renderedUrl()).endsWith("&workingDayInHours=6");
    }

    /**
     * The script resizes the iframe from the height the app posts, and it finds the frame by the id
     * the same render call wrote. A mismatch leaves the report clipped at its minimum height.
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
                .contains("getElementById('" + id.getValue() + "')")
                .contains("timesheet-app-height");
    }

    /**
     * Any window on the page may post a message, so the listener takes the height from the frame it
     * created and from nothing else, and only when the height is a number.
     */
    @Test
    void theScriptAnswersItsOwnFrameOnly() {
        when(scope.projectId()).thenReturn("elibrary");
        new TimesheetReportWidgetRenderer(context).render(builder);

        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(scriptContent).javaScript(script.capture());

        assertThat(script.getValue())
                .contains("event.source !== frame.contentWindow")
                .contains("typeof data.height !== 'number'")
                .contains("!isFinite(data.height)");
    }

    @Test
    void theIframeCarriesNoBorderAndNoScrollbars() {
        when(scope.projectId()).thenReturn("elibrary");
        new TimesheetReportWidgetRenderer(context).render(builder);

        verify(attributes).byName("scrolling", "no");
        verify(attributes).width("100%");
        verify(attributes).style(anyString());
    }
}
