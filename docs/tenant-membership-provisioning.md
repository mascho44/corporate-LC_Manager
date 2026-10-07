# Controlled membership provisioning — preparation

Stage 29 is a database foundation, not an enabled administration feature.
V64 supplies an invoker-rights, database-owner-only provisioning function. Public
execution and public access to its transient command table are explicitly revoked.
No application endpoint, additional tenant or tenant switching is enabled.

The function requires an active existing identity and a role belonging to the
target tenant. It rejects duplicate memberships and home-tenant assignments.
The existing home-user synchronization remains authoritative. Credentials,
global activation, existing memberships and local suspension states are unchanged.
Commands are removed in the same transaction; failures roll back the entire call.
The original direct membership write guards remain enabled throughout.

Verification: run all migrations and `tenant-shared-identity-check.sql` in disposable
PostgreSQL only. The fixture lifts the single-tenant constraint inside its rolled-back
transaction, never in production. It checks provisioning, duplicate and foreign-role
rejection, inactive/home identity rejection, public execution revocation, unchanged
identity fields, independent suspensions and identity-deletion cascades.

Stage 30 adds an internal server-scoped service and transactional audit logging.
It rejects the bootstrap tenant before looking up any identity, requires an
authenticated actor with a live active membership and USER_MANAGE permission in
the current server-owned context, and serializes administration using the tenant
lock. The target tenant is never supplied as a request parameter. Only an explicitly
named existing active identity may be assigned; foreign roles and duplicates are
rejected. No credentials or global user fields are modified. The allowlisted audit
event USER_MEMBERSHIP_CREATED joins the same transaction as provisioning.
There is still no endpoint, UI or configuration flag enabling this service.

Stage 31 (local, not deployed) prepares access suspension for shared identities in
non-bootstrap tenants. It checks live actor permissions and current ownership,
protects self-access and the last reachable tenant administrator, and audits before
and after in the same transaction. Administrator counts use the selected membership
role, active global account, active membership and tenant-local suspension, rather
than the identity's home role. Only the current tenant's suspension is written;
global identity fields and other memberships are untouched. No public API is added.

Stage 32 (local, not deployed) adds internal shared-identity role administration.
V65 provides an invoker-rights, owner-only role command; direct membership write
guards remain enabled. The service validates live tenant administration permission,
same-tenant roles and foreign-home identity membership. It protects the last active
administrator and the caller's user-management permission. Only the selected
membership role changes; global identity/home role, activation and local suspensions
remain unchanged. Audit joins the same transaction. No endpoint or tenant switch
is enabled. Tests include disposable PostgreSQL migration/role-update checks and
Java authorization, last-admin, ownership and suspension preservation checks.

Stage 33 adds real Spring transaction tests for shared-identity administration.
Audit failures must roll back both a native membership role update and a newly
created suspension. Successful operations must preserve the home role, credentials,
home membership and another tenant's independent role/access state. These tests use
H2 repositories and a transaction-bound JDBC replacement for the PostgreSQL role
function; they verify service transaction behavior, not PostgreSQL trigger behavior.
The actual PostgreSQL function and direct-write guards are verified separately by
the disposable PostgreSQL fixture. Stages 32–33 remain local, not deployed.

Before exposing this capability, implement membership-based administration for foreign identities,
and shared identity lifecycle handling. Only after isolation and authentication tests
pass should tenant creation and tenant selection be enabled. SQL provisioning must
not be exposed directly to clients. This preparation has not been deployed.
