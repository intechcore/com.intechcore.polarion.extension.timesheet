# Timesheet Reports for Polarion ALM

This Polarion extension provides the ability to generate timesheet reports.

## Build

This extension can be produced using Maven:

```bash
mvn clean package
```

The build also compiles the React user interface in `ui/` and bundles it into the extension under `webapp/timesheet-app`. No separate Node installation is required.

## Installation to Polarion

To install this extension, copy `com.intechcore.polarion.extension.timesheet-<version>.jar` to `<polarion_home>/polarion/extensions/com.intechcore.polarion.extension.timesheet/eclipse/plugins`. The Maven build can do it for you:

```bash
mvn clean install -P local-install-into-polarion
```

The `POLARION_HOME` environment variable must point to the Polarion installation folder.

Changes take effect only after a restart of Polarion.

## Polarion configuration

The report is provided as a **Timesheet Report** Live Report widget:

1. Open (or create) a Live Report page and edit it.
2. Add the **Timesheet Report** widget (category *Reports*).

The widget presets the default scope (project or group) and users shown when the report opens. The report itself provides controls - a scope selector, a multi-user selector, and a from/to date range - defaulting to the current user and the current month. It renders one table per user (split into one block per calendar month) and can export the selection to PDF.

## REST API

This extension provides a REST API. Its OpenAPI specification can be obtained [here](docs/openapi.json).

## Release

Releases are published by GitHub Actions.

1. Run the **Bump Version & Release** workflow on `main` and choose `patch`, `minor` or `major`. It sets the release version in `pom.xml`, commits it and pushes the tag `v<version>`.
2. The **Release** workflow builds and tests that tag, then attaches the jars to a GitHub release.
3. A second job returns `main` to the next `-SNAPSHOT` version.

Nothing is published to a Maven repository yet.
