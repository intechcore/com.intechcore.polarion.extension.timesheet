# Security policy

## Reporting a vulnerability

Report a vulnerability privately through GitHub:
https://github.com/intechcore/com.intechcore.polarion.extension.timesheet/security/advisories/new
(the **Security** tab, **Report a vulnerability**). Do not open a public issue for it.
Write to polarion@intechcore.com if you cannot use that form.

We answer within a week. The fix goes into the next release, and its release notes name it.

## Supported versions

Only the latest release gets fixes.

## Scope

The extension code, its REST API, the React user interface in `ui/`, the build and the workflows
of this repository.

Vulnerabilities in upstream software (Polarion ALM, the `ch.sbb.polarion.extension.generic`
parent, and the Maven and npm dependencies) belong to the upstream project. Tell us as well if
this project is affected, so we can release a fix when the upstream fix is out.
