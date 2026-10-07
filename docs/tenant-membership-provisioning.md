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

Before exposing this capability, implement a server-scoped authorized service,
transactional audit logging, membership-based administration for foreign identities,
and shared identity lifecycle handling. Only after isolation and authentication tests
pass should tenant creation and tenant selection be enabled. SQL provisioning must
not be exposed directly to clients. This preparation has not been deployed.
