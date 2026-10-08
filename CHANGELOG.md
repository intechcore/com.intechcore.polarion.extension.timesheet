# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases before 0.1.4 are listed on the
[GitHub releases page](https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/releases).

## [Unreleased]

## [0.2.1] - 2026-10-08

### Added
- The topic **Timesheet** shows the report as a page of a project or of the repository. In a
  project it shows that project only.

### Fixed
- A month that nearly fits the widget fits: the days narrow down to 38px rather than scroll
  for a few pixels. They stay at 48px on a wide widget.
- A work item in the report opens in Polarion, in the whole window. Its link resolved against
  the frame of the report and opened the report again.

## [0.2.0] - 2026-10-07

### Added
- The export button reads **Export to PDF** and shows Polarion's PDF export icon before its label.
- A PDF export of the whole page shows the report as the person exporting last showed it on
  screen, in the controls the widget lets them change.
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
- A custom period with a day that does not exist, such as 2026-02-30, opens the report on the
  current month instead of a shifted date.
- A project group with a space in its path, such as `/Demo Projects`, can be the scope of the
  report. The validation no longer filters characters: every id goes into the query quoted, and
  an id or a scope Polarion does not know is answered with 400.
- The tables of a PDF export of the whole page have the columns of the report's own PDF: one
  WorkItem width, the longest month across the page and a shorter month as a shorter table.
- The report a PDF export writes on the server starts with its scope and period, as the PDF
  of the report itself does. An empty month no longer reads as missing hours.
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
