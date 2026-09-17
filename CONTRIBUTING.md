# Contributing

## Development setup

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

## Pull request process

1. Fork the repository.
2. Create a feature branch (`git checkout -b feat/amazing-feature`).
3. Make your changes.
4. Make sure the build passes (`mvn clean verify`).
5. Commit your changes (`git commit -m 'feat: add amazing feature'`).
6. Push to the branch (`git push origin feat/amazing-feature`).
7. Open a pull request.

`main` takes squash merges only, and every commit on it is signed.

## Commit messages

This project uses [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` - new feature
- `fix:` - bug fix
- `docs:` - documentation changes
- `test:` - adding or updating tests
- `refactor:` - code refactoring
- `chore:` - maintenance tasks

A pre-commit hook checks the message, and so does the pull request workflow. Install the hooks:

```bash
pre-commit install
```

`.pre-commit-config.yaml` declares both hook types, so this one command installs the commit-msg
hook as well.

## Code style

- Java follows the conventions of the `ch.sbb.polarion.extension.generic` parent project.
- The user interface is formatted by Prettier and checked by ESLint. Run `npm run format` and
  `npm run lint` in `ui/`.
- The UI test suite keeps a 90% coverage gate on all four metrics.

Additional guidelines:

- Follow the existing code patterns.
- Do not write tests that assert log output. Assert the effect instead.

## Reporting issues

- Use GitHub Issues.
- Include the steps to reproduce.
- Include the Polarion, Maven and Java versions.
