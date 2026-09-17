// The Live Report widget embeds the report app in an iframe, and an iframe does not size itself to
// its content. The app measures its content and posts the height; this listener applies it.
//
// It lives in a file, not in a Java string, because its behavior is what has to be tested: the Java
// side can assert the text of a script, never what the script does. TimesheetReportWidgetRenderer
// serves this file and calls the function with the id of the iframe it just wrote;
// ui/test/widgetHeight.test.ts loads the same file and drives it in a real browser.
function timesheetSyncIframeHeight(frameId) {
    var frame = document.getElementById(frameId);

    window.addEventListener('message', function (event) {
        // One sender only: the frame this widget created. Every window may post to this page, and a
        // message is matched by source identity, which cannot be spoofed, rather than by its origin,
        // which a proxied Polarion changes.
        if (!frame || !event.source || event.source !== frame.contentWindow) {
            return;
        }

        var data = event.data;
        if (!data || data.type !== 'timesheet-app-height'
                || typeof data.height !== 'number' || !isFinite(data.height)) {
            return;
        }

        // Two pixels for the borders of the last table, which the content height leaves out.
        frame.style.height = (data.height + 2) + 'px';
    });
}
