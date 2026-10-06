# Tenant and language migration

## Bootstrap stage (not full multi-tenancy)

V49 introduces a single default tenant. Existing users and root records receive
its stable identifier; child records remain associated through their parent.
Bank is the planned primary module; Corporate is optional. Module flags are
metadata only at this stage, not authorization decisions and not UI switches.

The database explicitly forbids a second tenant (`tenant_bootstrap_single`).
At bootstrap, repositories and jobs were not yet tenant-filtered. Do not remove this
constraint or offer tenant creation/switching until isolation is complete.
Global reference uniqueness, company singleton storage and training data/model
storage still need conversion. Existing behavior and business data are preserved.

## Languages

English is the default; German is an optional bundled pack. `i18n.js` provides
key-based text lookup, English fallback, DOM text-only rendering and locale-aware
date/number formatting. Pack paths are centrally registered, not user supplied.
`/api/profile/language` reads/updates only the authenticated user's preference,
with tenant fallback and a neutral `USER_LANGUAGE_UPDATED` audit event.
Navigation/account actions are migrated first. The main app's existing Intl
formatters now use the selected locale. Existing detailed screens, validation
errors and generated documents are NOT completely translated yet.

Pack extension requires registering the code/asset/locale in the UI registry and
supported backend languages. No executable or user-uploadable language modules.
Missing German keys fall back to English. Original documents, SWIFT text, customer
names, references and business inputs must never be machine-translated or rewritten.
Dates in SWIFT/storage remain protocol/ISO values, independent of display language.

## Required next stages before multi-tenant release

1. Memberships and roles scoped per tenant; server-validated active tenant context.
2. Map root ownership in entities and enforce scope on list, ID lookup, mutation,
   downloads, exports, OCR/training, document generation and background queues.
3. Replace singleton/global uniqueness and add composite foreign-key protections;
   isolate model files, training knowledge, caches, outbox and audit queries.
4. Negative two-tenant tests: guessed UUIDs, cross-tenant relationships, background
   jobs, stale sessions, exports and privileged roles must not leak data.
5. Only then enable tenant management/switching and bank/corporate navigation.
6. Migrate remaining screen texts, API error codes/validation and audit presentation
   to the language-key abstraction; test English completeness and optional fallback.

The bootstrap migration must be tested on PostgreSQL against a representative
backup before production deployment. This stage is not authorization to deploy.

Local verification: all SQL migrations through V49 were applied to an isolated
PostgreSQL 17 database. A synthetic pre-existing user and LC received the default
tenant correctly; inserting a second tenant was rejected by the bootstrap check.
The temporary test database was removed. This does not replace backup-scale testing.

## Access isolation stage 1

LC list/ID/reference/amendment-lock/assignment/count lookups now use server-owned
`TenantContext`. LC writes/removal and role/user lifecycle writes validate ownership.
User administration lists, target IDs and administrator/role usage counts are scoped;
global username lookup remains for identity authentication and password-reset flows.
Assignment checks require an active user from the current tenant. Roles are mapped
to a tenant and administration lookups are scoped. V50 adds a composite foreign key
preventing cross-tenant user/role assignments in PostgreSQL.

Verified sessions derive context from the stored account, not a browser header.
Invalid/mismatched tenant roles revoke the session. Context is cleaned up even when
the request fails. Authentication still refuses non-default tenants and the V49
database gate remains in force. Outside request scope the default tenant is a
temporary bootstrap fallback for existing jobs, NOT a multi-tenant job strategy.

This is still NOT complete multi-tenancy: inherited repository bulk/pagination APIs,
document-only routes, training, jobs, exports and other roots need conversion.
Multiple memberships per identity and tenant switching are not implemented yet.
Do not enable additional tenants based only on the initial repository tests.

Verification: two synthetic tenants in repository tests covered LC ID/reference
reads, amendment locks, assignment projections/counts, foreign LC updates, user
lists/admin counts and role lookups. Session tests cover ignored browser tenant
headers, invalid tenant-role pairs, absent verified sessions and scope cleanup.
All SQL migrations through V50 passed on isolated PostgreSQL 17 with a synthetic
existing user. A second tenant was enabled ONLY in that disposable test database
to verify the composite role constraint: a cross-tenant assignment was rejected
and a same-tenant assignment accepted. The test database was removed.
