# CLAUDE.md

Guidance for working in this repository.

## What this is

`com.intechcore.polarion.extension.timesheet` - a Polarion ALM extension that generates
timesheet reports. It builds on the SBB `ch.sbb.polarion.extension.generic` framework
(parent POM) and targets **Polarion 2606 / Tomcat 11 / Jakarta EE 11**.

## Build & verify

```bash
mvn clean package            # do NOT pass -s .mvn/settings.xml locally, it is for CI only
```

- The React UI build is **inherited from the generic parent** (its `ui-build-react-app` profile, activated
  by the presence of `ui/package.json`): it installs Node, runs `npm ci` + `npm run build` in
  `ui/`, and copies `ui/dist` → `src/main/resources/webapp/timesheet-app/`. This pom carries no
  UI build of its own - do not add one back.
- **Tests** run on `mvn test`/`package`: Java via surefire (JUnit 5 + AssertJ + Mockito,
  inherited from the parent), and the frontend suite through the parent's test-phase execution.
  `-DskipJsTests=true` skips only the JS tests. Frontend tests live in `ui/test/**` and run in
  **Vitest browser mode** (a real Chromium via Playwright), with an istanbul **90%** gate on all
  four metrics, plus visual-regression references in `ui/test/expected/` (regenerate only with
  `npm run test:update:docker`). Playwright **e2e** (`ui/e2e/*.spec.js`, REST mocked via
  `page.route`) **also runs in the build**, in the `test` phase after the unit suite; skip it with
  `-DskipE2eTests=true`.

UI dev loop (hot reload against a running Polarion):

```bash
cd ui
cp .env.local.template .env.local   # set VITE_BASE_URL and (optional) VITE_BEARER_TOKEN
npm run dev                          # http://localhost:5173/?feature=report  (or ?feature=about)
npx tsc --noEmit                     # type-check (vite build does NOT type-check)
```

## System tests

They run against a **running Polarion** and are **never part of CI**. Two suites, both manual:

```bash
# The REST side: the report answers for data the test prepares itself.
POLARION_TOKEN=<personal access token> mvn -Psystem-tests test

# The report the server renders, in the browser.
cd ui && POLARION_TOKEN=... npm run systest         # the rendered report, on this machine
POLARION_TOKEN=... npm run systest:docker           # the same, plus the screenshot, in the pinned image
POLARION_TOKEN=... npm run systest:update:docker    # rewrites ui/systest/expected/
```

- `src/test/java/.../system/*SystemTest.java` is excluded by surefire by default; the `system-tests`
  profile includes only those. Without a Polarion answering, every one of them **skips**. Anything
  else fails the run rather than skipping it: a missing `POLARION_TOKEN`, a server that answers with
  500, and an https certificate the JVM does not trust. A skip there would report a green run that
  prepared nothing and called nothing. Both suites also check their own cleanup: a delete the run
  believed and the server refused leaves the fixture behind and doubles the next report.
- **The data is prepared by the tests**, in `elibrary` (override with `POLARION_SYSTEST_PROJECT`) and
  in **March 2030**, far from any real record. Work records are created per run and deleted
  afterwards. Work items are not: Polarion's REST API refuses to delete one (405), so they carry a
  marker in their title and are found again.
- **Each suite owns its fixtures**: the Java one books on `systest timesheet java`, the Playwright
  one on `systest timesheet ui`. Running both at the same time is therefore safe. Running the *same*
  suite twice at once is not: the second run reseeds the fixture the first one is reading.
- **Polarion writes a duration as `3d 1/2h`**: a half hour is a fraction, not `30m`. `3h 30m`,
  `3.5h` and `210m` are all rejected with 400.
- **REST v1 takes a token and nothing else**: a session is answered with 401. The files of the webapp
  are the other way round, a session and not a token, or Polarion answers with the login page.
- **Polarion answers only requests whose Host header matches `base.url`** (`http://localhost`), and a
  browser writes that header from the URL it opens. Inside the container the suite therefore reaches
  Polarion through `systest/host-bridge.mjs`, a TCP forward from `localhost` on the port
  `POLARION_URL` names: 80 by default, 443 under https.

## CI

GitHub Actions runs five workflows:

- `ci.yml`: the `build` job runs `mvn -s .mvn/settings.xml clean verify` with the Java, UI and e2e
  tests and the Polarion compatibility check. It fails when the build changes `docs/openapi.json`.
  The `pre-commit` job runs all hooks, except `no-commit-to-branch`, the git identity check and the
  Docker UI tests.
- `actionlint.yml`: lints the workflows when they change.
- `pr.yml`: checks the pull request title and its commits with commitizen.
- `bump-version.yml`: dispatched by hand with `patch`, `minor` or `major`. It sets the release
  version in the pom, commits it, tags it `v<version>` and pushes. It checks out with `PAT_TOKEN`
  on purpose: a tag pushed with the default `GITHUB_TOKEN` starts no further workflow, so
  `release.yml` would never see it.
- `release.yml`: runs on a `v*` tag. It runs `deploy` with the parent's `gpg-sign` and
  `central-publishing` profiles, so the tag is tested, signed and published to Maven Central under
  `com.intechcore.polarion.extensions`, and the jars are attached to a GitHub release. A second job
  returns `main` to the next `-SNAPSHOT`. The Central credentials are the organization secrets
  `SONATYPE_USERNAME`, `SONATYPE_TOKEN`, `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`. The `central`
  server lives in `.mvn/settings.xml`, because the release build passes that file with `-s` and
  never reads the one `setup-java` writes.

The Polarion artifacts come from the Intechcore Nexus through the repository secrets `NEXUS_URL`,
`NEXUS_USERNAME` and `NEXUS_PASSWORD`. `.mvn/settings.xml` declares Central first, so Nexus only
serves the Polarion artifacts. The Maven cache leaves `com/polarion` and `com/siemens` out, because
pull requests from forks can restore the caches of `main`.

## Architecture

Two layers: a Java/Polarion backend and a React frontend.

**Java** (`src/main/java/.../timesheet/`)
- `widget/TimesheetReportWidget` + `TimesheetReportWidgetRenderer` - the Live Report
  widget. The renderer emits an `<iframe>` to the React SPA (`/polarion/timesheet-app/ui/app/
  index.html?feature=report&...`) with the configured parameters as query string, plus a
  `postMessage` height-sync script. Parameter definitions live in the widget; rendering is
  client-side.
- `rest/controller/TimesheetInternalController` (`@Hidden`, `@Path("/internal")`) - REST
  data API: `/internal/users/{user_id}/timesheet` (single user) and `/internal/timesheet?
  user_ids=a,b` (multi-user). `TimesheetApiController` (`@Secured`, `@Path("/api")`) extends
  it to expose the public, bearer-authenticated `/api/...` surface.
- `manager/TimesheetReportManager` - queries Polarion work records and maps them to the
  `model/*` DTOs (`Timesheet`, `WorkRecord`, `WorkItem`, `Project`, `User`; Lombok `@Data`).
- `TimesheetAppServlet` - a `GenericUiServlet` subclass serving the `timesheet-app` webapp
  context, which holds the Vite bundle, the administration-menu icons and the generated
  `html/about.html`. There is no `timesheet-admin` webapp any more.
- The admin **About** entry in `hivemodule.xml` opens the SPA directly at `?feature=about`.
- The extension does **not** serve `/configuration-properties` itself: generic provides it, and
  a second resource on that path makes Jersey reject the whole REST application at startup.

**React** (`ui/`) - Vite + React 19 + TypeScript SPA on the shared
`@sbb-polarion/react-sbb-polarion` (RSP) library, served as static resources from the
`timesheet-app` webapp. One app, page selected by `?feature=` (`report` | `about`); an unknown
or missing feature falls back to the report, which is what the widget embeds:
- `components/ReportView.tsx` - interactive report. Controls: a scope picker (from
  `/internal/scopes`), a multi-select user picker (from `/internal/users`), `DateRangePicker`
  and `ExportPdfButton`. Defaults to the current user
  (`/internal/current-user`) and the current month; refetches `/internal/timesheet` on change.
  Renders **one `UserTimesheet` per selected user** (period total in the heading), each split
  into **one `TimesheetBlock` per calendar month** (`groupDatesByMonth`). A block covers only its
  own month: months with no records are skipped and each block lists just the work items booked
  in it (`recordsWithin`). The widget's query params
  (`scope`/`userIds`/`workingDayInHours`) seed the initial scope and user selection.
- PDF export (`utils/exportPdf.ts`, lazy-loaded) mirrors that layout (per-user, per-month
  blocks) using `jsPDF`. The on-screen and PDF layouts are intentionally kept in sync.
- `pages/About.tsx` - RSP's shared `About` component (manifest table, configuration properties,
  the REST-token test and the README help article). It replaced a hand-written copy of generic's
  about.jsp that lived in `components/AdminView.tsx`.
- The scope and user pickers are RSP's `SearchableSelect`: single-select with per-option Polarion
  scope icons (`iconURL`) and tree indentation, and `multiple` for the users, which renders the
  checkbox popup and the removable chips. Both replaced hand-rolled components (RSP gained the
  multi-select in v0.1.0). Their width is set on `.searchable-dropdown` - the component hides the
  `<select>`, so a rule targeting `select` does nothing.
- `services/useRemote.ts` - REST hook. **Convention: the UI always calls `/internal/*`
  (in-session); external callers use `/api/*` with a PAT bearer token.**

The frontend follows the same shape as the ~20 converted SBB extensions (two webapp contexts,
`?feature=` routing, RSP components, browser-mode tests).

Webapp contexts must be declared in `src/main/resources/plugin.xml` (extension point
`com.polarion.portal.tomcat.webapps`) - adding a `webapp/<name>/WEB-INF/web.xml` is not
enough. The widget/embedding URL must point at a real file (`.../ui/app/index.html?...`),
not the directory (`GenericUiServlet` rejects extension-less paths with "Unsupported file
type").

## Conventions & gotchas

- **Jakarta, not javax.** All code is on `jakarta.*`. `src/` must stay free of `javax.*`
  EE imports. `web.xml` descriptors use the Jakarta `web-app_6_1.xsd` (version 6.1).
- **Don't hardcode `Require-Bundle`** in `META-INF/MANIFEST.MF`. The list is supplied by the
  `generic` parent (so it tracks the correct 2606 bundle names, e.g.
  `com.fasterxml.jackson.module.jakarta.xmlbind.annotations`). Keep only `Bundle-Name` /
  `Export-Package` / `Support-Email` in source.
- **Built SPA is git-ignored** (`webapp/timesheet-app/app/`, regenerated each build), along
  with `ui/node*`, `ui/dist`, `ui/.env*.local`. `ui/package-lock.json` IS committed. Cleaning
  the built app is handled by the parent - this pom no longer needs its own `clean-ui-build`.
- **Fonts**: the UI uses Polarion's standard font stack
  (`'Segoe UI', 'Selawik', 'Open Sans', Arial, sans-serif`) - no bundled font. The PDF uses
  jsPDF's built-in `helvetica`. (Note for future: `GenericUiServlet` serves
  `.js .html .css .png .svg .gif .woff .woff2 .ico .txt` but **not `.ttf`**, and Polarion's CSP
  blocks `data:` fonts - so any bundled web font must be a served woff2.)
- **PDF/print export of the report widget can't capture the client-rendered iframe** - that's
  why export is done client-side via the report's **Export PDF** button (`utils/exportPdf.ts`).
  It imports `jspdf-autotable/es`, the package's ESM entry: the default CommonJS one resolves to
  the module namespace rather than the function under the browser-mode test transform, which
  made the module impossible to load in a test.
- **The widget gets whatever width the Live Report column gives it** - about 690px when the page
  puts it in a column of a multi-column layout (`.polarion-rp-column`), against a ~1280px page. The
  iframe already asks for `width: 100%`, so a wider report is a page-layout change, not a code one.
  No month fits that width, hence the label column sized off `vw` and the always-painted scrollbar.
- **The report tables are `table-layout: fixed` with `width: max-content`.** Under the automatic
  layout a table is capped at its container and the spare width goes to the columns, so a 28-day
  month drew a wider WorkItem column than a 31-day one. Both properties are needed: `fixed` alone
  still lets the browser squeeze the table. Long months scroll in `.timesheet-table-wrap`.
- **The iframe height must account for the picker popups.** They are `position: fixed` portals on
  `<body>`, so they add nothing to `body.scrollHeight` and do not resize `body` - the widget's
  iframe kept its height and cut the option list off. `useIframeAutoHeight` measures
  `.sd-portal .options` as well (the portal itself is a zero-height anchor). Measure only the
  popups: a full-viewport fixed overlay would report a bottom that grows with the iframe.
- **Tests that render the report must pin the clock** (`vi.useFakeTimers({ toFake: ['Date'] })`,
  or `page.clock.setFixedTime` in e2e). The period defaults to the current month and empty months
  draw nothing, so a fixture in a fixed month stops rendering once the month rolls over.
- **autoTable applies `columnStyles` to body cells only.** Head and foot cells fall back to the
  global `styles`, and a column takes the widest of its cells - so a numeric `styles.cellWidth`
  silently overrides a per-column width in those two rows. Set such widths in `didParseCell`,
  which runs for every section. A test watches for autoTable's "could not fit page" warning.
- **Build-log noise is treated as an error.** `frontend-maven-plugin` labels everything npm and
  Vite write to stderr as `[ERROR]`. `ui/.npmrc` drops npm to `loglevel=warn`, and
  `vite.config.js` installs a `customLogger` that hides "doesn't exist at build time" for
  `/polarion/` paths (Polarion serves those at runtime). Keep both filters narrow.
- **OpenAPI**: `docs/openapi.json` is regenerated by the swagger-jakarta plugin on build.
- **Commits**: a pre-commit hook requires `user.email` to match
  `firstname.lastname@intechcore.com`. Breaking commits use `feat!: ...` or a `BREAKING CHANGE:`
  footer.
