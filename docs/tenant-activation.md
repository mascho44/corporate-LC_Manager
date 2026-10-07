# Tenant creation and workspace selection

Tenant creation and selection deployed via PR #112. Migration V66 removes the single-tenant
bootstrap constraint. Membership write guards and same-tenant foreign keys remain.

## User workflow

1. Sign in to the default tenant as an administrator with user-management permission
   and two-factor authentication.
2. Open **Tenants** in the left menu. Enter a unique lowercase code, a name and the
   default language. Create the tenant. It starts with no business data and its own
   administrator role; the creator receives that role in the new tenant.
   New tenants also receive Editor and Viewer system roles using
   the existing permission defaults. No users are automatically assigned these
   roles. Existing tenants and customized roles remain unchanged. All three roles
   are created within the same audited tenant-creation transaction.
3. Select the workspace using the header selector or **Open workspace**. The page
   reloads. Only this tenant's records and roles are accessible.
4. Under **Users**, assign an explicitly named existing identity to a local role.
   Local role changes and suspension do not change global credentials or other
   tenants. The last accessible administrator and self-access are protected.

Global accounts are created in the default tenant. Creating another tenant does not
copy existing LC files, training documents, templates, companies or other business
data. Each tenant must configure its own companies/templates. Profile settings,
password and TOTP remain own-identity self-service. Disabling TOTP is forbidden if
the identity has active administrator access in any tenant. The deployed login
initially uses the default tenant. This release adds an optional tenant
code to login: blank uses the default tenant; an explicit code requires active access
in that tenant, but not in the default tenant. Credentials remain global and accounts
are still created centrally. Global IAM lifecycle administration remains open.

## Security and verification

Deployed via PR #117: Tenants includes a selected-tenant setup summary
showing company-record, document-template and active-member counts, with explicit
buttons opening the existing administration forms. It is a presence check, not a
certification of data completeness or document-template validity. Suspended and
globally inactive members are excluded. The read-only endpoint requires an active
ADMIN membership, TOTP, USER_MANAGE and SETTINGS_MANAGE. No arbitrary tenant ID is
accepted and all counts use tenant-scoped repository queries. Other users do not
request these counts. A setup-summary error leaves normal tenant navigation usable.

Deployed via PR #118: the Tenants page shows the selected workspace's name and login
code to all of its authenticated members, including read-only users. Guidance
explains explicit tenant login, existing global credentials, role assignment and
the default-tenant behavior of an empty login code. It uses the existing authorized
workspace list; there is no public tenant discovery or extra administrative access.

Deployed via PR #119: selecting another workspace requires an explicit confirmation
showing its name and code and warning about unsaved changes before reloading.
Cancel/Escape leaves the selected workspace and session unchanged; concurrent
switch requests are suppressed while confirmation is pending. This is a general
warning, not unsaved-change detection or automatic draft saving.

After successful creation, the page displays an explicit open-workspace
action and setup guidance, only when the refreshed authorized workspace list
contains the new tenant. Creation itself does not select or reload the workspace.
The action uses the same confirmed, server-authorized selection flow.

Refresh responses are generation-checked: only the latest requested page refresh
may update workspace choices, settings, setup counts or loading errors. A late
response does not reinstate stale editing controls or overwrite newer values.

Loading has an accessible status and busy indicator. A failed workspace/settings
request offers a reload button; success clears the old error and restores the
workspace selector. Older requests cannot end the latest request's busy state.

The selected tenant's administrators with
USER_MANAGE and active TOTP can change its name and default language on the Tenants
page. Code, ID and Bank/Corporate profile flags remain unchanged. Updates and
before/after audit are one transaction; failed audit rolls back the update. A user
with a personal language preference retains that preference.

Explicit tenant login resolves a private code,
verifies the password and membership within an explicitly opened target scope, and
binds that UUID in the pending second-factor session. TOTP rechecks membership and
credentials before authentication; missing or malformed pending targets are denied.
Global identity/TOTP operations explicitly use the home scope. The framework login
always checks DEFAULT even when called inside another scope. No public tenant list is
exposed. Unknown tenants, inaccessible memberships and incorrect credentials produce
the same login failure. Username/IP throttling remains shared across tenant codes.

The authenticated user's choices come only from their active memberships. Tenant
IDs in arbitrary request parameters/headers do not set the context. The explicit
selection endpoint checks membership, global activation, local suspension and
administrator TOTP before saving the selected UUID and new role/permission stamp.
It rotates the session ID, retains the original absolute session expiry and requires
CSRF. The next request revalidates all access. Changed permissions, credentials,
deactivation or suspension revoke the session.

Creation and initial membership/role are transactional with audit. Selection audit
must succeed before changing the session. Background inbox/outbox workers enumerate
persisted tenants, open explicit scopes and restore the thread context after each
job, including failures. Tenant profile flags remain metadata, not authorization.

Tests cover Spring transactions and audit rollback, session scoping/revocation,
CSRF and unauthorized selection/creation, ownership and repository isolation, plus
the actual PostgreSQL migrations and write guards using disposable data. No ICC
documents or rule packs are part of this feature.

Verification on 2026-10-07: 428 Java test cases (426 passed, two skipped),
56 JavaScript tests passed. A disposable Docker installation applied all 66
PostgreSQL migrations. `scripts/test-tenant-workspaces.cjs` then passed TOTP login,
tenant creation/selection, native membership-role update, stale-session rejection,
own profile/language access and two-way LC list/detail isolation. The script is
hardcoded to localhost and synthetic credentials; never run it against production.

The follow-up release passed 446 Java cases (444 passed, two skipped) and 59
JavaScript tests. The expanded PostgreSQL smoke test passed settings updates,
read-only denial and explicit tenant login for an identity whose default membership
was suspended, with foreign LC details remaining inaccessible.

Extended PostgreSQL smoke verification on 2026-10-07 passed default Admin/Editor/
Viewer role provisioning, zero company/template counts and one initial active
member, read-only settings, denied setup counts and administration for Viewers,
denied LC import/deletion, and unchanged workspace/LC data after rejected writes
or an inaccessible workspace selection. The test also retains two-way LC isolation
and explicit login with suspended default-tenant access. Its disposable local app
requires a synthetic TOTP_ENCRYPTION_KEY of at least 32 characters. No production
data was used; test containers, temporary database volume and network were removed.

Deployed via PR #120: membership role choices include their current permission set.
Both existing-user assignment and role changes preview these additional permissions
before submission. Selection alone never updates a membership; mutation requests
still contain only roleId (and username for an initial assignment). This preview
does not replace server authorization or describe every base-role capability.

Local follow-up: the membership overview, prospective-role preview and role-editor
checkboxes use readable EN/DE labels for all current permission identifiers.
Unknown permissions remain visible with their original identifier; stored roles
and assignment payloads are unchanged. Coverage checks both language catalogs
against the backend permission enum.

The role editor's local search filters by the translated permission label or
technical identifier. It hides labels without rebuilding or disabling checkboxes,
so selected permissions remain part of the save payload even when filtered out.
Resetting the role form clears the search; a live status reports the match count.

Deployed via PR #122: OCR queue claims validate the inbox item's tenant before reading
its content or changing its claim. Regression coverage includes a faulty repository
returning a foreign item, a foreign OCR failure followed by successful default-
tenant processing, and preservation of the caller's scope. Failed OCR does not
change another tenant's queued work or its completion state.

Local follow-up: the outbox service explicitly checks ownership of every returned
dead-letter message before exposing the list, and checks ownership before resetting
a failed message for retry. These checks supplement tenant-scoped repository queries
and persistence guards. A retry queues the same message; it does not immediately
publish it. Cross-tenant failure states remain unchanged on rejection.

Local follow-up: direct document extraction, background extraction and application
of page-local recognition explicitly validate document ownership before reading
content or changing extraction metadata. Ownership failures propagate as access
denials rather than being converted into an OCR failure on a foreign document.

## Repeatable local acceptance test

Run `bash scripts/test-tenant-workspaces.sh` with Docker, curl and Node.js 20+.
It builds the current source in a uniquely named Compose project, binds only
localhost port 18086, and uses fixed synthetic credentials and a disposable TOTP
key. It waits for both API health and initial-user provisioning before testing.
Production configuration and existing application volumes are not used.
On success, failure or a handled interrupt it removes its own test containers,
database volumes and network. Test data is intentionally not retained. A port
conflict fails without stopping the application already using that port.

Verified on 2026-10-07: the one-command runner completed the PostgreSQL smoke
successfully and removed its test containers and network automatically. No
Compose-labeled containers, networks or volumes remained for its unique project.

## Continuous acceptance checks

The membership overview offers a local username, role-name and translated-status
search with a live match count. Filtering never submits a write or searches other
tenants. Clearing the search restores every row; refreshing the overview resets
the filter. Role/access controls are preserved when rows are hidden.

The CI workflow runs the same disposable PostgreSQL acceptance test as a separate
**Tenant acceptance (PostgreSQL)** job on pull requests, pushes to main and manual
workflow runs. It checks the actual database migrations, tenant provisioning,
workspace switching, session revocation and read/write access boundaries, alongside
the existing Java and JavaScript tests. The job has a 20-minute limit and uses a
GitHub-hosted runner with read-only repository permissions and no retained checkout
credentials. It does not use deployment secrets, production hosts or uploaded
documents. The local runner handles its own normal/error cleanup; GitHub discards
the hosted runner after cancellation or a job timeout.

Adding the job does not automatically make it a required branch-protection check.
That repository setting must be configured separately if merges should be blocked
until this check passes.

The acceptance script also creates a synthetic company in each workspace. It
checks empty company lists/choices in the newly created tenant, setup counts after
company creation, foreign-ID read and update rejection in both directions, and
unchanged home-company data after a rejected foreign update. Viewer company
creation and updates must fail; the authenticated company-choice list must still
contain only the selected tenant's unchanged company. No real company data is used.
The expanded local PostgreSQL run passed on 2026-10-07, including automatic removal
of the disposable test containers and network. The application/API behavior was
not changed; unavailable company IDs retain the existing HTTP 400 response.
