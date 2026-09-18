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
        // The frame this widget created, and the document it was given. Source identity alone is
        // not enough: contentWindow stays the same object across a navigation, so a document from
        // somewhere else loaded into this very frame would keep passing that check. The widget
        // embeds the app by a relative URL, so its document is always same origin with this page,
        // whatever host Polarion answers on.
        if (!frame || !event.source || event.source !== frame.contentWindow
                || event.origin !== window.location.origin) {
            return;
        }

        // A height is a number of pixels the app measured, so it is finite and not negative.
        // A -1 would otherwise collapse the report to a single pixel.
        var data = event.data;
        if (!data || data.type !== 'timesheet-app-height'
                || typeof data.height !== 'number' || !isFinite(data.height) || data.height < 0) {
            return;
        }

        // Two pixels for the borders of the last table, which the content height leaves out.
        frame.style.height = (data.height + 2) + 'px';
    });
}
