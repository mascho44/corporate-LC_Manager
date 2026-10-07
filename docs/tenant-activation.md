# Tenant creation and workspace selection

Tenant creation and selection deployed via PR #112. Migration V66 removes the single-tenant
bootstrap constraint. Membership write guards and same-tenant foreign keys remain.

## User workflow

1. Sign in to the default tenant as an administrator with user-management permission
   and two-factor authentication.
2. Open **Tenants** in the left menu. Enter a unique lowercase code, a name and the
   default language. Create the tenant. It starts with no business data and its own
   administrator role; the creator receives that role in the new tenant.
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
