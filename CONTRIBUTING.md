# Contributing

Issues and pull requests are welcome.

## Build and test

### Prerequisites

- Java 21
- Maven 3.6.3+
- Polarion 2606, if you want to install the extension locally
- Node and npm, if you want to work on the user interface. The Maven build installs its own Node
  for `ui/`, but the commands under [The user interface](#the-user-interface) call yours.

### Building

```bash
mvn clean verify                                   # everything: Java, UI and end-to-end tests
mvn clean package -DskipTests                      # skip the Java tests
mvn clean verify -DskipJsTests=true                # skip the UI unit tests
mvn clean verify -DskipE2eTests=true               # skip the Playwright end-to-end tests
mvn clean install -P local-install-into-polarion   # install into $POLARION_HOME
```

Each flag skips one suite, so combine them to skip more than one. The end-to-end tests need no
running Polarion: they start their own Vite server and answer the REST calls themselves.

The build compiles the React user interface in `ui/` and bundles it under
`webapp/timesheet-app`. Polarion loads a new jar only after a restart.

### The user interface

```bash
cd ui
cp .env.local.template .env.local   # set VITE_BASE_URL
npm run dev             # hot reload against a running Polarion
npm run test            # Vitest in a real browser
npm run lint
npx tsc --noEmit        # vite build does not type-check
```

The visual tests compare against reference images that are pixel-locked to a pinned Playwright
Docker image. Regenerate them with `npm run test:update:docker`, never by hand.

### System tests

They talk to a running Polarion and are never part of CI, so they are not needed to send a pull
request. They prepare their own work records in March 2030 and delete them afterwards.

```bash
POLARION_TOKEN=<personal access token> mvn -Psystem-tests test   # what the REST API answers
cd ui && POLARION_TOKEN=... npm run systest                      # the report the server renders
```

`CLAUDE.md` describes what they need and what they change.

CI runs actionlint and zizmor, the Maven build with all tests and SonarCloud, the pre-commit
hooks and the commit message check for every pull request. A pull request from a fork gets no
secrets, so its `build` job cannot fetch the Polarion artifacts and is skipped.

## Code style

- Java follows the conventions of the `ch.sbb.polarion.extension.generic` parent project.
- The user interface is formatted by Prettier and checked by ESLint. Run `npm run format` and
  `npm run lint` in `ui/`.
- The UI test suite keeps a 90% coverage gate on all four metrics.

Additional guidelines:

- Follow the existing code patterns.
- Do not write tests that assert log output. Assert the effect instead.

## Pull requests

1. Branch from the default branch as `type/description`, for example `fix/empty-title`.
2. Keep one change per pull request. New behavior comes with tests; a bug fix adds a test that
   fails without it.
3. Write commit messages as [Conventional Commits](https://www.conventionalcommits.org/) without a
   scope: `feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`, `test: ...`, `build: ...`,
   `ci: ...`, `chore: ...`. A pre-commit hook checks the message, and so does CI. Install the
   hooks with `pre-commit install`; `.pre-commit-config.yaml` declares both hook types.
4. Sign your commits. The default branch accepts verified signatures only.
5. Add an entry under `## [Unreleased]` in `CHANGELOG.md`, written for users: the release notes
   quote it. Update the README when behavior or configuration changes.

Pull requests are squash-merged once all required checks are green.

## Releases

A maintainer runs the Bump Version workflow. It moves the Unreleased entries into a versioned
section, tags the release, and the Release workflow publishes it with signed build provenance.

## Reporting issues

- Use GitHub Issues.
- Include the steps to reproduce.
- Include the Polarion, Maven and Java versions.
