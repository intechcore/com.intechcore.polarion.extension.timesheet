package com.intechcore.polarion.extension.timesheet;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the webapp context this servlet passes to {@code GenericUiServlet}. The base class serves a
 * request only when its URI starts with {@code /polarion/<webAppName>/ui/} and strips exactly that
 * prefix, so the string has to stay identical to {@code src/main/resources/webapp/timesheet-app/}
 * and to every URL in {@code META-INF/hivemodule.xml}.
 */
class TimesheetAppServletTest {

    private TimesheetAppServlet servlet;
    private ServletContext servletContext;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        servletContext = mock(ServletContext.class);
        ServletConfig servletConfig = mock(ServletConfig.class);
        when(servletConfig.getServletContext()).thenReturn(servletContext);

        servlet = new TimesheetAppServlet();
        servlet.init(servletConfig);
        response = mock(HttpServletResponse.class);
    }

    private HttpServletRequest requestFor(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void servesFromTheAppWebappContext() throws Exception {
        when(servletContext.getResourceAsStream(anyString())).thenReturn(null);

        servlet.service(requestFor("/polarion/timesheet-app/ui/app/index.html"), response);

        // The context prefix is stripped, so what is looked up is the path inside the webapp.
        verify(servletContext).getResourceAsStream("/app/index.html");
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void rejectsAnotherWebappContext() {
        // The extension's REST context: a request meant for it must not be served from this app.
        HttpServletRequest request = requestFor("/polarion/timesheet/ui/app/index.html");

        assertThatThrownBy(() -> servlet.service(request, response))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported resource path");
    }

    @Test
    void servesTheWidgetIcon() throws Exception {
        when(servletContext.getResourceAsStream(anyString())).thenReturn(null);

        servlet.service(requestFor("/polarion/timesheet-app/ui/images/widget-icon.svg"), response);

        // The URL TimesheetReportWidget.getIcon returns has to resolve inside this webapp.
        assertThat(servlet).isNotNull();
        verify(servletContext).getResourceAsStream("/images/widget-icon.svg");
    }
}
