# Timesheet Reports for Polarion ALM

[![CI](https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/actions/workflows/ci.yml/badge.svg)](https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/intechcore/com.intechcore.polarion.extension.timesheet/badge)](https://scorecard.dev/viewer/?uri=github.com/intechcore/com.intechcore.polarion.extension.timesheet)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/14796/badge)](https://www.bestpractices.dev/projects/14796)
[![Release](https://img.shields.io/github/v/release/intechcore/com.intechcore.polarion.extension.timesheet)](https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.intechcore.polarion.extensions/com.intechcore.polarion.extension.timesheet)](https://central.sonatype.com/artifact/com.intechcore.polarion.extensions/com.intechcore.polarion.extension.timesheet)
[![Java 21](https://img.shields.io/badge/java-21-blue.svg)](https://openjdk.org/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=coverage)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=bugs)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.timesheet&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.timesheet)

This Polarion extension provides the ability to generate timesheet reports.

## Build

This extension can be produced using Maven:

```bash
mvn clean package
```

The build also compiles the React user interface in `ui/` and bundles it into the extension under `webapp/timesheet-app`. No separate Node installation is required.

## Installation to Polarion

The released jar is published to
[Maven Central](https://central.sonatype.com/artifact/com.intechcore.polarion.extensions/com.intechcore.polarion.extension.timesheet)
and attached to every [GitHub release](https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/releases).

To install this extension, copy `com.intechcore.polarion.extension.timesheet-<version>.jar` to `<polarion_home>/polarion/extensions/com.intechcore.polarion.extension.timesheet/eclipse/plugins`. The Maven build can do it for you:

```bash
mvn clean install -P local-install-into-polarion
```

The `POLARION_HOME` environment variable must point to the Polarion installation folder.

Changes take effect only after a restart of Polarion.

### Verify

GitHub releases after 0.1.3 carry a signed provenance bundle. Verify a downloaded jar with
`gh attestation verify <file> --repo intechcore/com.intechcore.polarion.extension.timesheet`.

## Polarion configuration

The report is provided as a **Timesheet Report** Live Report widget:

1. Open (or create) a Live Report page and edit it.
2. Add the **Timesheet Report** widget (category *Reports*).

The widget presets the default scope (project or group) and users shown when the report opens. The report itself provides controls - a scope selector, a multi-user selector, and a from/to date range - defaulting to the current user and the current month. It renders one table per user (split into one block per calendar month) and can export the selection to PDF.

## REST API

This extension provides a REST API. Its OpenAPI specification can be obtained [here](docs/openapi.json).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the build, the tests and the pull request rules.

## Disclaimer

This software is provided "as is", without warranty of any kind, as the [LICENSE](LICENSE) states.
Use it at your own risk. Intechcore GmbH is not liable for damage from its use, as far as the law
allows. It is published free of charge, outside of any commercial offering, with no obligation to
support it. Security reports are welcome, see [SECURITY.md](SECURITY.md).

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
