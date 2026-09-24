# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security
- OpenSSF Scorecard runs weekly and on every push to `main`. The README shows its badge.
- CodeQL also analyzes the GitHub Actions workflows.
- zizmor audits the workflows next to actionlint.
- The release creates the GitHub release with the `gh` CLI instead of a third-party action.
- Renovate pins every GitHub Action by its commit digest.

Earlier releases are listed on the
[GitHub releases page](https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/releases).
