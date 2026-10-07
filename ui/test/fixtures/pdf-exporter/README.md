# Style sheets of the PDF Exporter

`dle-pdf-export.css` and `wiki.css` as the PDF Exporter ships them, unchanged:
`default/dle-pdf-export.css` and `default/wiki.css` of
`ch.sbb.polarion.extensions:ch.sbb.polarion.extension.pdf-exporter:13.10.1` on Maven Central.
The PDF Exporter joins the two into the style sheet of a Live Report export.

`PrintedReport.visual.test.tsx` applies them to the report the widget writes for a PDF export, so its
pixel reference shows what the export does.

Copyright SBB, under the Apache License 2.0:
https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.pdf-exporter/blob/main/LICENSE

To update them, take the two files from a newer release and regenerate the references:
`npm run test:update:docker`.
