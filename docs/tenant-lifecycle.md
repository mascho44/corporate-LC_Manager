# Central tenant lifecycle and module profiles

Tenant creation now belongs exclusively to Platform administration. The legacy
`POST /api/tenants` always denies creation, including for local administrators.
`POST /api/platform/tenants` requires a live, active platform administrator with
TOTP. It creates an empty tenant, its standard roles and the creator's initial
administrator membership in the existing audited transaction. It works from any
accessible workspace; no local role implies global platform authority.

The platform page lists all tenants and provides a profile selector and
suspension/reactivation action. `PUT /api/platform/tenants/{id}` accepts only
activation and Bank/Corporate flags; code, name and language cannot be overwritten
by that operation. At least one profile must be selected. Local presentation
settings and local role management remain separate.

Suspension preserves all business data, memberships, roles and credentials.
The default tenant and the caller's current workspace cannot be suspended.
Switch to another workspace first. Subsequent requests in a suspended tenant
invalidate authenticated sessions; login and workspace selection are denied.
Suspended tenants disappear from ordinary workspace choices and invitation
targets. Invitations cannot be accepted into a suspended tenant. Enumerated
workers skip suspended tenants; an already-running request/job is not forcibly
interrupted. Reactivation restores tenant eligibility, not independently revoked
global accounts or suspended local memberships.

## Modules

Profile permissions intersect with existing role permissions; enabling a module
never grants a user a permission. The server checks the live selected tenant on
every business request, not a client tenant header or a cached UI profile.

| Module | Bank | Corporate | Combined |
| --- | --- | --- | --- |
| LC files, inbox, SWIFT/OCR training, document checking | Yes | Yes | Yes |
| Document generation, draft creation/editing/submission, company templates | No | Yes | Yes |
| Advising processing/training, approval thresholds, draft review/approval | Yes | No | Yes |

Existing drafts remain readable in both profiles. Bank may review and approve
them but not generate new documents; Corporate may prepare and submit them but
not approve them. Combined supports the complete workflow within one tenant.
No profile change transfers data between tenants. Audit, user self-service and
administration retain their independent permission checks.

Migration V69 adds `tenant.active`, preserving all existing tenants as active.
Lifecycle/profile changes use the home-administration lock and a target-row lock.
The before/after audit includes only activation and profile flags and commits
with the change; audit failure rolls back both. Profile changes apply to subsequent
requests. The current UI reloads after changing its own tenant profile.

## Verification (2026-10-07)

The full backend regression passed, followed by final targeted security and
transaction tests after the live scalar authorization check was added. The
combined reports contain 533 cases, no failures/errors and two skipped cases.
All 109 JavaScript tests passed. The final disposable PostgreSQL acceptance
applied V69 and passed central-only tenant creation, explicit local-role checks,
Bank/Corporate API gates, current/default suspension protection, revocation of a
live session, denied login while suspended and reactivation with retained LC data.
The synthetic SMTP/invitation regressions also passed. Test containers, database
volumes and networks were removed; production was not changed.

### Follow-up acceptance (2026-10-08)

The disposable PostgreSQL/SMTP API workflow passed again. The acceptance runner
now additionally generates a synthetic commercial invoice as PDF and DOCX in a
Corporate-enabled tenant, checks their MIME types, non-empty sizes and downloaded
PDF/ZIP signatures, denies generation for a Viewer and for a Bank-only profile,
and verifies that both generated documents remain listed after suspension and
reactivation. Existing documents remain readable when generation is disabled.
The installation removed its own containers, database and network afterward.
No production data or real email recipients were used. This is automated API
acceptance, not a visual browser/Word/PDF layout or usability review.
