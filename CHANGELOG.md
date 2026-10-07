# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases before 0.1.4 are listed on the
[GitHub releases page](https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/releases).

## [Unreleased]

### Added
- The widget option **Show hours of** opens the report on the selected users or on its viewer.
- The widget settings show only what applies: the users, the custom dates and the **Allow
  changing** options appear when they take effect.
- The widget options **Allow changing scope**, **Allow changing users** and **Allow changing
  period** lock a control of the report at what the settings preset. They combine freely.
- The widget option **Period** opens the report on the current month, the previous month or
  a custom range.
- The widget option **Hide controls** shows the tables only.
- A PDF export or a print of a page shows the report of the widget, read on the server, instead of
  an empty frame.

### Changed
- The version bump moves the Unreleased entries of this changelog into a section for
  the new version. The GitHub release takes its notes from that section.
- The release no longer waits for an approval of the `release` environment.
- CI runs actionlint and zizmor in a `lint` job on every pull request, and the commit
  message check as a CI job. CodeQL runs as the GitHub default setup.
- CI installs pre-commit and commitizen from requirements files pinned by hash.
- A release fails early when its GitHub release exists already. Releases are immutable and
  get all their files in one step.
- Renovate refreshes `ui/package-lock.json` through lock file maintenance, reviewed by a person.

### Fixed
- The WorkItem column has the same width in every table. A long title wraps onto more lines.
- The WorkItem column stays in view while the days scroll.
- The horizontal scrollbar of a report table is visible in Chrome on macOS. It starts after
  the WorkItem column, and the table has no vertical scrollbar.
- A failed request shows its error alone. The users no longer read "total: 0 h" under it,
  nor the answer of the previous request, and Export PDF is disabled.
- The scope list leaves out the projects the user may not read. One such project made the
  list fail, and the Scope field stayed empty.

## [0.1.4] - 2026-09-24

### Security
- OpenSSF Scorecard runs weekly and on every push to `main`. The README shows its badge.
- CodeQL also analyzes the GitHub Actions workflows.
- zizmor audits the workflows next to actionlint.
- The release creates the GitHub release with the `gh` CLI instead of a third-party action.
- Renovate pins every GitHub Action by its commit digest.
- GitHub releases from now on carry the pom and a signed build provenance bundle (`*.intoto.jsonl`).
- Renovate takes its common rules from the shared preset `github>intechcore/renovate-config`, which also turns on OSV vulnerability alerts.
