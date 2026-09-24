# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases before 0.1.4 are listed on the
[GitHub releases page](https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/releases).

## [Unreleased]

### Changed
- The version bump moves the Unreleased entries of this changelog into a section for
  the new version. The GitHub release takes its notes from that section.
- The release no longer waits for an approval of the `release` environment.

## [0.1.4] - 2026-09-24

### Security
- OpenSSF Scorecard runs weekly and on every push to `main`. The README shows its badge.
- CodeQL also analyzes the GitHub Actions workflows.
- zizmor audits the workflows next to actionlint.
- The release creates the GitHub release with the `gh` CLI instead of a third-party action.
- Renovate pins every GitHub Action by its commit digest.
- GitHub releases from now on carry the pom and a signed build provenance bundle (`*.intoto.jsonl`).
- Renovate takes its common rules from the shared preset `github>intechcore/renovate-config`, which also turns on OSV vulnerability alerts.
