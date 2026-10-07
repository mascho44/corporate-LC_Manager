# Platform administration — first stage

Platform administration is distinct from a tenant's ADMIN role and USER_MANAGE
permission. V67 grants the separate platform_administrator flag only to the
existing home identity named `admin`, as explicitly selected by the operator.
Other identities default to false, including newly created tenant administrators.
For a fresh installation, the initial identity receives this flag only when its
normalized username is `admin`. Existing installations without that identity do
not silently promote another user. No API currently grants or revokes this flag.

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

This first stage does not yet create global accounts, send invitations, administer
platform grants or retire the legacy default-tenant account-creation workflow.
Those are follow-up stages; the new page currently manages global activation only.

Verification on 2026-10-07: 41 targeted backend tests and 99 JavaScript tests passed.
The disposable PostgreSQL acceptance installation applied V67 and passed platform
access from a non-default workspace, local-user denial, self-suspension rejection,
global suspension of a shared identity, revocation of its already-open foreign
session, and restoration of foreign login without lifting the suspended default
membership. No production data was used.
The full backend regression completed 480 cases without failures or errors (two
skipped). The test installation removed its own containers and network afterward.
