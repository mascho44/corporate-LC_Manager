# Tenant and language migration

## Bootstrap stage (not full multi-tenancy)

V49 introduces a single default tenant. Existing users and root records receive
its stable identifier; child records remain associated through their parent.
Bank is the planned primary module; Corporate is optional. Module flags are
metadata only at this stage, not authorization decisions and not UI switches.

The database explicitly forbids a second tenant (`tenant_bootstrap_single`).
Existing repositories and jobs are NOT tenant-filtered yet. Do not remove this
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
