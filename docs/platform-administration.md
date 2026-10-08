# Platform accounts, invitations and grants

Platform administration also manages [tenant creation, suspension and module
profiles](tenant-lifecycle.md). Local tenant roles cannot create tenants or change
global lifecycle/profile flags.

Platform administration is distinct from a tenant's ADMIN role and USER_MANAGE
permission. V67 grants the separate platform_administrator flag only to the
existing home identity named `admin`, as explicitly selected by the operator.
Other identities default to false, including newly created tenant administrators.
For a fresh installation, the initial identity receives this flag only when its
normalized username is `admin`. Existing installations without that identity do
not silently promote another user. A protected API now grants and revokes this
flag independently of tenant roles.

The **Platform administration** menu opens a page, not a dialog. An active flagged
identity with enrolled TOTP can list global home identities and activate/suspend
accounts without switching to the default workspace. Normal authenticated-session
and selected-membership validation still applies: this is not a separate platform
login independent of all tenant memberships.

`GET /api/platform/access` reports only whether the current identity can use the
page. `GET /api/platform/users` and `PUT /api/platform/users/{id}/access` validate
the live platform flag, global activation and TOTP enrollment on every call.
Mutations require CSRF. Local roles never imply platform access. Reads and writes
open an explicit home scope and restore the caller's selected scope afterward.
Responses never contain password hashes, TOTP secrets or recovery codes.

Global suspension denies subsequent authenticated requests in every tenant.
Activation restores only global eligibility: suspended memberships and assigned
roles remain unchanged. Self-suspension and suspension of the last active,
TOTP-enrolled platform administrator are forbidden. Administration uses the shared
home-tenant lock, and allowlisted before/after activation audit commits in the same
transaction. Audit failure rolls back the account update.

Platform identities cannot be changed or deleted through legacy tenant identity
administration, and cannot disable their own TOTP. Existing local role/membership
administration remains separate. Credential self-service remains available.

`POST /api/platform/users` remains a central, platform-only manual creation API.
Only a live platform administrator with TOTP may create an identity. Email is
required; username uniqueness is global and case-insensitive. Initial passwords
use the existing strength policy and are hashed, never returned or audited.
Requests cannot select roles, activation, tenant IDs or platform grants.
The identity uses the empty-permission system VIEWER role in the bootstrap tenant;
its automatically generated home membership is suspended in the same transaction.
Thus creation grants no business access, not even read access to the default
tenant. An authorized local administrator subsequently assigns an explicit role
in the intended tenant through the existing membership workflow. Global account
activation never lifts the bootstrap suspension. Creation and audit are atomic.
The normal UI uses invitations instead of administrator-selected passwords.

## Invitations

Choose a tenant and one of that tenant's roles on the platform page; permissions
are previewed before sending. Email is mandatory. `POST /api/platform/invitations`
creates an inactive identity with `invitation_pending=true` and a suspended home
membership. No business membership is provisioned yet. A 256-bit random secret is
sent only by email; the database stores its SHA-256 hash, expiry, identity/email/
credential binding, issuer and selected tenant/role. The role-permission snapshot
is also bound: changed permissions require a fresh invitation.

Links expire after 24 hours. `invitation.html#token=…` immediately removes the
fragment from browser history, uses no storage and submits only an HTTPS POST
body to `/api/auth/invitation/accept`. This bearer-secret endpoint is intentionally
public and CSRF-exempt; it validates input and applies a bounded IP rate limit.
It exposes no user directory. Acceptance atomically hashes the recipient's chosen
password, activates the identity, grants the explicitly selected membership,
consumes the token and writes audit. For an explicit default-tenant invitation,
only that home membership is enabled; a foreign invitation leaves home access
suspended. Local ADMIN recipients must complete the existing mandatory TOTP setup
at login. No invitation ever grants platform rights.

Pending identities cannot sign in, request password resets, or be activated by
the global activation endpoint. Tokens fail after revocation, replacement,
expiry, credential/email changes, role-permission changes, or issuer suspension/
platform-grant revocation. Self-service public completion is serialized with
platform administration; cached token rows are checked against current storage.

The pending list displays expiry, attempts, next retry, a safe error category and
`PENDING_MAIL`, `MAIL_RETRY`, `SENT`, `MAIL_CANCELLED`, `MAIL_FAILED` or `EXPIRED`.
Resend replaces the old secret; revoke deletes it while retaining the
blocked identity. A matching blocked identity may be reinvited with an explicitly
selected target; existing active identities cannot be overwritten.
Invitation issuance and its encrypted mail payload commit atomically. A background
worker resumes persisted work after restarts and retries SMTP failures up to five
attempts, after 30 seconds, 2 minutes, 10 minutes and 30 minutes. It rechecks account,
issuer, tenant and role eligibility before delivery. Expired or cancelled payloads,
successful payloads and exhausted retries are cleared. Resend creates a fresh token
and resets attempts. Pre-V70 invitations have no queued payload and require manual
resend. No real mail is sent by the disposable tests.

The temporary mail payload includes the bearer link encrypted with AES-256-GCM,
a random nonce and invitation-hash binding. Configure `app.mail.invitation-encryption-key`
with at least 32 characters or use the existing TOTP key with domain-separated key
derivation. A missing key prevents issuance; changing the key makes old queued mail
unreadable and requires resend. Backups may retain encrypted payloads according to
backup retention. Neither plaintext links nor SMTP exception details appear in the
administration API. SMTP calls use bounded timeouts and hold the administration lock
for one delivery. SMTP acceptance followed by a process/database failure can cause
a repeated email: delivery is not guaranteed exactly once.

Local verification (2026-10-08): cipher/queue unit tests, real Spring/H2
transactions (issuance rollback without a key, persisted SMTP failure and retry),
frontend tests and disposable PostgreSQL V70 acceptance with a synthetic SMTP sink
passed. This does not constitute a production SMTP or visual layout test.

## Platform grants and retired creation

`PUT /api/platform/users/{id}/platform-grant` accepts only `{granted: boolean}`.
Only a current platform administrator may invoke it. Promotion requires an
active identity with enrolled TOTP and no pending invitation. Own revocation and
removal of the last active TOTP-enrolled administrator are denied. Decisions are
serialized using the home-tenant lock and audited in the same transaction; local
roles are unchanged. Live service checks deny a revoked grant even to an already
open, otherwise valid session.

Authenticated sessions now record explicit successful TOTP verification. Merely
enrolling 2FA in another session cannot upgrade an older password-only session.
Existing TOTP sessions without the new proof must sign in again after deployment.

Legacy `POST /api/users` is denied in every tenant, including the default tenant.
The Users page displays local membership/role/access controls, not the old global
creation/edit form. Role management and identity self-service remain available.

## Verification

The invitation/grant implementation passed the full backend regression: 519
cases, no failures/errors, two skipped; all 107 JavaScript tests passed.
Disposable PostgreSQL acceptance applied V68 and exercised real in-memory
synthetic SMTP, foreign and default invitation acceptance, no login before
acceptance, role isolation, replaced/revoked/replayed token rejection, required
TOTP before platform promotion, protected self-revocation and live denial after
grant revocation. Transaction tests verify rollback of password, activation,
membership, invitation issuance and platform grants on audit failure.
The test installations removed their own containers and networks; no production
data or real mail was used. The synthetic SMTP sink binds port 18087 only for the
test runner, never relays messages and retains them only in process memory.

Earlier foundation verification on 2026-10-07: 41 targeted backend tests and 99 JavaScript tests passed.
The disposable PostgreSQL acceptance installation applied V67 and passed platform
access from a non-default workspace, local-user denial, self-suspension rejection,
global suspension of a shared identity, revocation of its already-open foreign
session, and restoration of foreign login without lifting the suspended default
membership. No production data was used.
The full backend regression completed 480 cases without failures or errors (two
skipped). The test installation removed its own containers and network afterward.

Local account-creation verification: 37 targeted backend tests and all 101
JavaScript tests passed. Disposable PostgreSQL acceptance confirmed denial for
local-only users, required email, duplicate rejection, no default/foreign login
before assignment, explicit local assignment, continued default-login denial and
viewer-only access in the assigned tenant. Audit-failure rollback was verified
with real Spring transactions. The test installation was removed afterward.
