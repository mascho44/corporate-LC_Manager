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

## Access isolation stage 2: documents and inbox OCR

Inbox lists, ID/content lookups, mutation locks and extraction candidates are
scoped to `TenantContext`. Inbox ownership is immutable and lifecycle writes
validate it. LC document lists, counts and ID/content lookups require both current
tenant ownership and a matching LC owner. Document writes validate that parent
relationship. V51 backfills document ownership from the existing LC and adds a
composite PostgreSQL foreign key preventing cross-tenant LC/document links.

Claimed inbox OCR work carries the stored tenant into extraction and completion;
the scope is restored afterwards. The existing scheduled dispatcher still uses
the default bootstrap tenant: this is not yet multi-tenant scheduling. Training
sessions/learning storage, audit ownership/queries, exports and other roots remain
pending. Inherited repository bulk/pagination APIs are not generally isolated.
Authentication and the V49 single-tenant database gate remain unchanged.

Verification: all 300 Java tests completed without failures (two skipped), and all
30 JavaScript tests passed. Negative repository/service tests cover foreign inbox
IDs/locks/queue candidates, foreign document downloads/lists and mismatched LC
links. Worker tests cover foreign-job exclusion and claimed-tenant propagation.
All migrations through V51 passed on isolated PostgreSQL 17; a synthetic existing
document received its LC owner. A cross-tenant link was rejected, a valid link was
accepted and LC deletion cascaded correctly. The disposable test database was
removed. Production data and tenant gates were not changed by these checks.

## Access isolation stage 3: training and learned corrections

Training sessions have immutable tenant ownership with lifecycle write/removal
checks. History, ID/content/snippet/export lookups, edit locks, quality aggregates
and the learning service's source-session reads use scoped repository queries.
This covers SWIFT and advising-letter training. Existing draft ownership/permission
checks remain; learned knowledge is shared across authorized users of the same
tenant, not restricted to the creator of the source session.

Learning controls now have a composite `(tenant_id, rule_id)` identity. Identical
rule hashes can be activated independently in different tenants. V52 retains
existing activation settings and prevents cross-tenant training/LC links. Deleting
an LC clears only the training link, not the session's tenant or learned facts.
The existing soft-delete of templates still retains learning source data.

PostgreSQL verification with synthetic pre-existing sessions/controls confirmed
preserved activation, independent same-hash controls, rejection of foreign LC links
and preservation of training ownership after LC deletion. The isolated temporary
database was removed. Repository/service tests exercise foreign history/ID/locks,
write rejection, tenant-local corrections and activation, shared learning for a
different user, and retained learning after template soft-delete.
The complete Java and JavaScript regression suites passed with no failures.

This remains an incremental bootstrap: audit and other roots, generic repository
bulk/pagination operations, global uniqueness/singletons and multi-tenant job
dispatch still need conversion. The single-tenant database/authentication gates
remain mandatory; neither tenant creation nor switching is enabled by this stage.

## Access isolation stage 4: audit and integration outbox

Audit events carry immutable server-context tenant ownership. Recent history/CSV,
entity timelines and case-package audit queries are scoped, including direct IDs
and source lists. Existing limits (200 recent events, 100 per entity) remain in
place. JPA also rejects event updates/removal; PostgreSQL's existing append-only
UPDATE/DELETE/TRUNCATE triggers remain unchanged. V53 adds tenant-aware indexes
without rewriting historical events or disabling triggers.

Integration outbox records created by audit events inherit the same tenant.
Pending candidates, status/dead-letter lists, counts and retry IDs are scoped.
Publication verifies ownership before invoking the publisher, not after delivery.
The scheduled dispatcher still processes only the bootstrap tenant; routing and
multi-tenant dispatch are not enabled. Authentication events without an established
tenant context still belong to the bootstrap tenant. These paths must be designed
before enabling multiple tenants. Generic inherited bulk/pagination APIs also
remain outside this incremental conversion.

Negative repository/service tests cover identical entity IDs across tenants,
CSV exclusion, audit write rejection, outbox ownership propagation, foreign retry
and prevention of foreign publication before any publisher call. Isolated
PostgreSQL 17 migrations through V53 preserved a synthetic historical event and
confirmed its original tenant. UPDATE, DELETE and TRUNCATE were all rejected by
the unchanged triggers. The disposable test database was removed; no production
history or tenant gates were changed.
The full regression suite passed: 305 Java tests with no failures (two skipped)
and 30 passing JavaScript tests.

## Access isolation stage 5: company profiles and document templates

Company profiles and document templates have immutable tenant ownership and
lifecycle write/removal checks. Company lists/choices, IDs and logo access use
scoped lookups. The legacy default-profile route resolves the first profile by ID
within the current tenant, not global company ID 1; an empty tenant fails closed.
Default-profile audit events now use the actual selected company ID.

Template lists, downloads, deletion, company-specific lookup and legacy-name or
wildcard fallback are scoped. A supplied company ID must resolve in the current
tenant before fallback. Word rendering rejects a foreign LC before selecting data
or templates. V54 adds composite LC/company and template/company foreign keys
and tenant-scoped template uniqueness, allowing independent wildcard templates.

The single-tenant activation gates remain in force. Generic repository bulk and
pagination APIs, remaining roots, tenant provisioning/default-profile creation and
multi-tenant background dispatch are not completed by this stage.

Verification: all migrations through V54 passed on isolated PostgreSQL 17.
Two synthetic tenants could independently store wildcard invoice templates;
cross-tenant LC/company and template/company links were rejected. The temporary
database was removed without modifying production or discarding application data.
Repository/service tests cover foreign IDs and updates, tenant-local default
company/logo access, template downloads/deletion and scoped fallback selection.
The complete regression suite passed: 307 Java tests without failures (two
skipped) and 30 JavaScript tests passed.

## Access isolation stage 6: SWIFT import history and charges

Import records, charge profiles and estimates carry immutable tenant ownership
with lifecycle checks. Import history retains its 20-item limit; status counts and
ID/source-list reads are scoped. Fee profiles and saved estimate lists/IDs are
scoped, including profile selection when calculating charges for an owned LC.
V55 attributes existing estimates through their LC and enforces composite links
to both the LC and tariff profile. The existing LC-delete cascade remains intact.

Two-tenant tests cover foreign import history/counts/IDs, tariff lists and IDs,
estimate histories and rejection of using a foreign tariff for an owned LC.
Foreign tariff modification is denied. PostgreSQL 17 migrations through V55 passed
in isolation; existing synthetic estimates received their LC tenant, foreign LC
and tariff links were rejected and deletion cascaded correctly. The temporary
database was removed; production data was not changed.
The complete regression suite passed: 310 Java tests with no failures (two
skipped) and 30 passing JavaScript tests.

## Access isolation stage 7: rule-pack storage and activation

Stored versions and selections have immutable tenant ownership. Version lists,
ID tests, duplicate-version checks and activation/deactivation locks are scoped.
Selections use composite `(tenant_id, id)` identities: identically named versions
can be imported and activated independently in different tenants. Evaluation
validates LC/document ownership before reading packs; version decoding also
validates ownership. Rights acknowledgement, checksums and pack tests remain intact.

V56 adds ownership to stored versions, tenant-scoped uniqueness and composite
foreign keys requiring active/previous references to match both tenant and pack ID.
Isolated PostgreSQL 17 migrations through V56 preserved a synthetic activation.
Identically named versions/selections in another test tenant were accepted;
foreign active/previous versions and another pack's version were rejected. The
temporary database was removed. No ICC packs or source PDFs were used.

Repository/service tests cover IDs/lists/duplicate checks/locks, foreign testing
and activation, independent deactivation and rejection of foreign LC evaluation.
Additional tenant activation remains gated: remaining roots, generic repository
bulk/pagination APIs, identities/provisioning and job dispatch need completion.
Production was not changed by this stage.
The complete regression suite passed: 312 Java tests without failures (two
skipped) and 30 passing JavaScript tests.

## Access isolation stage 8: LC case child records

Notes, tasks, amendments, document-check decisions and requirement mappings now
inherit immutable tenant ownership with lifecycle write/removal checks. LC-specific
lists, direct IDs, amendment duplicate checks, open-task queues and review/mapping
lookups are scoped. Note history retains its 100-item limit. Explicit decision
deletion resolves only current-tenant records before removing them.

V57 backfills each child from its LC and adds composite same-tenant LC links with
the existing delete-cascade behavior. Other remaining roots, generic repository
bulk/pagination operations, user membership/provisioning and background routing
remain pending. Additional tenants are still gated.

Verification: all 314 Java tests completed without failures (two skipped); all
30 JavaScript tests passed. Two-tenant tests covered child lists/IDs, the open-task
queue, amendment duplicates, review/mapping lookup and foreign decision deletion.
A loaded foreign task could not be changed. Isolated PostgreSQL 17 migrations
through V57 backfilled all five synthetic existing child types; every foreign LC
link was rejected and LC deletion cascaded correctly. The temporary database was
removed; production data and tenant gates were unchanged.

## Access isolation stage 9: document drafts and approval policy

Document drafts and approval thresholds inherit immutable tenant ownership.
Draft lists and direct lookups are scoped, including the service paths for
editing, submitting, finalizing and deleting. Existing maker/reviewer/approver
rules remain unchanged. Replacing approval thresholds deletes only the current
tenant's thresholds; identical currency/amount boundaries are valid independently
in different tenants.

V58 backfills draft ownership from each LC and enforces a same-tenant LC foreign
key with cascading deletion. Approval thresholds receive tenant ownership and
tenant-local uniqueness. Isolated PostgreSQL 17 migrations through V58 preserved
a synthetic existing draft, rejected a foreign LC link, preserved another
tenant's thresholds during deletion, and retained draft delete cascades. The
temporary database was removed; production and the additional-tenant gate were
not changed.

Verification: 316 Java tests completed without failures (two skipped), and all
30 JavaScript tests passed. Two-tenant tests cover inaccessible foreign drafts,
rejected editing/status changes/deletion and independent policy replacement.
Other remaining roots, inherited generic repository bulk/pagination APIs,
identity/provisioning and background routing still need completion before
additional tenants can be activated.

## Access isolation stage 10: comparison and email history

Document comparisons and email deliveries now inherit immutable tenant ownership.
History lists, direct IDs and unpaged lists are scoped. Case exports use the
same scoped comparison history. Mail attachments are resolved through scoped
document lookups rather than inherited, unfiltered bulk lookup; a missing or
foreign attachment is rejected before any mail interaction or delivery record
is created. Existing LC ownership checks continue to reject foreign mail history
and sending. Comparison snapshots remain available after source documents are
deleted, as before; document identifiers are historical references, not new
foreign keys to live documents.

V59 backfills both histories from the parent LC and adds same-tenant composite
LC foreign keys with the existing delete cascades. Isolated PostgreSQL 17
migrations through V59 preserved synthetic existing history, rejected foreign
LC links for both tables and retained cascading deletion. The temporary test
database was removed. No production changes or mail configuration changes were
made, and additional tenants remain gated.

Verification: 320 Java tests completed without failures (two skipped); all
30 JavaScript tests passed. Two-tenant tests cover history lists and IDs,
comparison/mail rejection of foreign documents, absence of external mail
interactions on rejection, and lifecycle rejection of foreign record updates.
Remaining generic repository bulk/pagination APIs, identities/provisioning and
background routing still need completion before additional tenants are enabled.

## Access isolation stage 11: standard repository APIs and scheduled scopes

All 23 business repositories now share `TenantScopedRepository`. Unpaged/sorted
lists, paginated results and their total counts are scoped. Multi-ID reads use
the concrete scoped ID lookup, including integer and composite identities, and
deduplicate requested IDs. Existence checks and reference lookups use the same
boundary; missing/foreign references fail before a lazy proxy can be returned.
A repository without its own scoped ID lookup fails closed.

ID-list deletion and all-record deletion resolve current-tenant records and
retain entity lifecycle checks. Generic JPQL batch deletion is disabled rather
than bypassing lifecycle ownership or append-only audit protection. Query-by-example
and fluent example queries are unused by the application and explicitly disabled;
new search features must use explicit scoped queries. Existing intentional scoped
operations such as approval-threshold replacement remain available.

Scheduled outbox dispatch explicitly opens the default-tenant context and restores
the previous scope even on failure. The asynchronous inbox worker explicitly opens
that context before claiming work, then retains the claimed record's tenant through
OCR and completion. Internal `processNext` continues to honor an explicitly supplied
scope for tests/future per-tenant dispatch. No tenant enumeration or provisioning is
enabled: schedulers still serve only the gated bootstrap tenant in production.

Identity/authentication repositories and the tenant registry are intentionally
outside this business repository base. User membership/provisioning, identity
repository APIs, multi-tenant job enumeration, global LC-reference uniqueness
and remaining language/module work still require completion. Additional tenants
remain forbidden by the bootstrap constraint. No new database migration is needed
for this stage, and production is unchanged.

Verification: the final complete suite ran 328 Java tests with zero failures or
errors (two skipped), plus 30 passing JavaScript tests. Negative tests cover
mixed-tenant ID lists, sorted/paginated reads and page totals, hidden references,
scoped multi-ID/all-record deletion, blocked batch/example/fluent APIs, integer
and composite identities, scheduled outbox scope restoration on success/failure,
and asynchronous inbox dispatch without caller-context leakage. Existing OCR
lease recovery and claimed-tenant propagation tests remain passing.

## Access isolation stage 12: user and role administration boundaries

User and role repositories now inherit the guarded standard repository APIs,
including scoped counts, pages, multi-ID reads and lifecycle-aware deletion.
Global username lookup and the locked identity lookup for password reset remain
explicit authentication boundaries; username uniqueness remains global. Initial
administrator detection now counts users in the bootstrap tenant rather than
unrelated tenants. Existing role ownership and same-tenant assignment checks
remain in place.

Profile/avatar access, password changes, TOTP settings/login verification and
language preferences validate the identity's owner before sensitive access.
TOTP setup also resolves and validates the user before creating a setup secret.
While non-default tenants remain disabled for login, reset requests for such
identities produce no token, audit event or outgoing mail; reset completion for
such an identity is rejected before credentials are changed or tokens consumed.
Existing default-tenant authentication and reset flows remain unchanged.

This is boundary hardening, not membership provisioning or tenant switching.
The bootstrap gate, global identity uniqueness and existing single-tenant user
ownership remain. Avatar/token repositories retain their identity-parent boundary;
they are not tenant-wide administration APIs. No SQL migration or production
change was made in this stage. Memberships, tenant provisioning and multi-tenant
job routing still require their own implementation and release review.

Verification: the complete regression ran 331 Java tests without failures or
errors (two skipped), plus 30 passing JavaScript tests. The isolation test class
was rerun successfully after adding reset-completion rejection coverage.
Negative tests cover standard user/role reads and foreign-ID deletion, profile
and avatar access, password/TOTP operations without crypto/avatar interaction,
and blocked reset requests/completion without mail, credential or token mutation.

## Access isolation stage 13: membership compatibility foundation

V60 introduces `tenant_membership`: a stable membership ID, tenant/user pair,
same-tenant role link and activation status. Existing users are backfilled without
changing their roles or active state. A PostgreSQL trigger synchronously mirrors
user creation and role/activation updates; user deletion cascades to membership.
User administration remains the sole write authority during this compatibility
stage. Direct membership insert/update/delete/truncate is blocked, while nested
user-trigger writes and FK cascades remain allowed. Membership identity is unique
per tenant/user; this does not grant permission to add another tenant.

The immutable entity and scoped repository expose an internal read model only.
`TenantMembershipService` returns a redacted view with current role permissions,
not credential hashes, TOTP secrets or raw user entities. It checks membership,
role and legacy user ownership. Permissions are read from the role, not copied
into membership, so later role-permission changes are reflected automatically.

Authentication still uses the existing user/role path. No management/switching
endpoint or new UI has been enabled. The compatibility model deliberately retains
legacy single-tenant user ownership; multi-tenant identity memberships and their
provisioning require an explicit subsequent migration, authorization design and
release review. The `tenant_bootstrap_single` constraint remains unchanged.

Isolated PostgreSQL 17 migrations through V60 preserved active/inactive users,
synchronized new users and role/activation changes, and retained deletion cascades.
Direct insert/update/delete/truncate was rejected; a foreign role link was rejected
by the composite membership FK, and the second-tenant bootstrap gate was verified.
The additional tenant and temporary guard changes were confined to the disposable
test database, which was removed. Production was not changed.

Verification: the final complete regression ran 333 Java tests without failures
or errors (two skipped), and all 30 JavaScript tests passed. Membership tests
cover foreign IDs/users/lists/page counts, role-permission inheritance including
subsequent changes, and rejection of direct read-model deletion.

## Stage 14: administrator membership overview

The existing user administration now includes a collapsible, read-only tenant
membership overview: tenant name, user, role, inherited permissions and active
status. English and German labels are included. Changes still use the existing
user administration; no independent membership write endpoint is introduced.

`GET /api/users/memberships` requires `USER_MANAGE`, derives the tenant from the
server-side context and returns a restricted DTO without credential fields.
Tenant switching remains explicitly disabled. The bootstrap tenant constraint
is unchanged, and no database migration is needed.

The UI renders names as text, isolates overview loading failures from user
management and ignores stale responses. Verification: 335 Java tests ran with
zero failures/errors (two skipped); all 34 JavaScript tests passed. Tests cover
anonymous and unauthorized requests, credential redaction, safe text rendering,
loading failures and stale responses. This increment has not been deployed.

## Stage 15: read-only tenant profile metadata

The administrator overview also displays tenant code, default language and Bank/
Corporate profile flags, labelled in English and German. These flags are metadata,
not authorization grants. The existing endpoint resolves settings only from the
server-side tenant context; client-supplied tenant parameters cannot select another
tenant. No editing, switching, new tenant provisioning or SQL migration is added.

All 35 JavaScript tests passed, including safe rendering of profile metadata. The
API security suite additionally verifies server-side tenant selection and profile
values. The preceding full Java regression remains the baseline; this increment
uses targeted API tests. Production remains unchanged.

Pre-release verification for stages 14–15: the complete Java regression passed
without failures/errors, and all 35 JavaScript tests passed. Deployment retains
the existing V60 schema and the bootstrap tenant gate.

## Stage 16: tenant-local LC reference uniqueness

V61 replaces the global reference constraint with `(tenant_id, reference)`
uniqueness. The replacement is added before removing the original V1 PostgreSQL
constraint. Existing tenant-scoped lookup and duplicate checks now match database
semantics; the JPA schema declaration uses the same composite constraint.
Same references across tenants no longer conflict, while duplicates within one
tenant remain rejected by the database, including concurrent writes.

No tenant activation, identity switching or provisioning is introduced. The V49
bootstrap gate remains intact. Repository tests cover shared references with
scoped lookups and same-tenant rejection. All SQL migrations through V61 and
`src/test/resources/tenant-reference-migration-check.sql` passed on disposable
PostgreSQL 17. The test temporarily relaxed the gate only inside a rolled-back
transaction and verified that it rejects second tenants beforehand. The
disposable database was stopped and removed. Production is unchanged.

Verification: the complete Java regression ran 338 tests with zero failures and
errors (two skipped). No frontend changes were required in this increment.

## Stage 17: tenant-local role name uniqueness

V62 replaces the V22 global role-name constraint with `(tenant_id, name)`
uniqueness, adding the replacement before removing the old constraint. The JPA
mapping matches the migration. Existing case-insensitive application duplicate
checks remain tenant-scoped; the database constraint, as before, compares exact
names. No case-insensitive database guarantee is claimed.

Identical role names can exist in separate tenants without sharing permissions.
Usernames remain globally unique. Existing system roles, same-tenant role foreign
keys, membership guards and the bootstrap tenant gate remain unchanged. Tests
cover cross-tenant names/permissions/visibility and same-tenant duplicate rejection.
All SQL migrations through V62 and the disposable PostgreSQL reference/role tests
passed, including preservation of the bootstrap Administrator role. Temporary gate
changes were rolled back. No additional tenant has been enabled in production.

Verification: the complete Java regression ran 340 tests without failures or
errors (two skipped). The disposable database was stopped and removed. Stages
16 and 17 remain local, not deployed.

## Stage 18: shared explicit background-job tenant boundary

`TenantJobRunner` provides a shared entry point for scheduled Outbox dispatch and
asynchronous inbox extraction dispatch. Its explicit tenant argument is checked
before running work; missing and non-bootstrap tenants are rejected. Worker code
does not select its tenant from a caller's request context. Scope cleanup restores
the previous thread context on success or failure, and job exceptions propagate
to the existing worker error handling.

This consolidates the existing explicit bootstrap worker scopes; it does not add
tenant enumeration, activate additional tenants or change processing budgets,
claim fencing, retry semantics or publication transaction behavior. Inbox work
retains its recorded ownership scope after claiming. Tests cover rejected jobs,
scope restoration, propagated failures and separate worker-thread execution.
There is no new SQL migration in this stage. Production is unchanged.

Verification: the complete Java regression passed without failures/errors. All
four job-runner tests passed in a subsequent targeted run including the final
worker-thread test. Whitespace validation passed. Stages 16–18 remain local.

## Stage 19: revoke stale session permissions

Successful password/second-factor authentication stores a deterministic digest of
the granted role authority and permission authorities in the verified session.
Each subsequent authenticated request compares this snapshot against the current
stored user role and effective role permissions before entering protected code.
Changes invalidate the session rather than retaining stale authorities or silently
upgrading a session. Missing snapshots also require a new login; therefore rollout
requires existing users to authenticate again. Pending 2FA sessions do not receive
an authorization snapshot before verification completes.

The digest sorts and deduplicates authorities. It contains no credential material.
Existing password, account activation, TOTP, lifetime and tenant checks remain.
This uses the existing stored user role semantics; it does not redesign identities
or enable tenant switching. No schema migration is required. Tests cover changed
permissions, missing legacy snapshots, deterministic role/default permissions and
snapshot creation after password-only login and completed 2FA. Production unchanged.

Verification: the complete Java regression passed without failures/errors. The
final authorization stamp, login/2FA and credential-session test suites were then
rerun successfully after the additional assertions. Whitespace validation passed.

## Stage 20: current assigned-role base type

`AppUser.getRole()` now derives its effective base type from the assigned role,
falling back to the legacy field only when no role is assigned. This avoids stale
copied role types after custom-role edits. Login authorities, user views, session
authorization snapshots and administrator TOTP checks use the same live value.
Permissions continue to come from the assigned role rather than base-type defaults.

Active-role counts use a left join and the same assigned-role/legacy fallback,
within the current tenant. Custom-role demotion is rejected if its active users
represent all remaining active administrators. Existing system-role restrictions
remain unchanged. This guard follows the current transaction/count approach; it
does not introduce serializable concurrent administrator-change enforcement.

Tests cover inherited base-type changes, login authorities, authorization snapshot
changes, mandatory administrator TOTP, tenant-scoped live counts and rejection of
last-administrator demotion through a role. No SQL migration or tenant activation
is introduced. Stages 19–20 remain local and not deployed.

Verification: complete Java regression passed without failures/errors. The final
role-inheritance tests were rerun successfully after adding the administrator
TOTP assertion. Whitespace validation passed.

## Stage 21: atomic administration change audit

User/role create, update and delete endpoints now participate in a shared
transaction with their successful audit event. `recordChangeInTransaction` uses
MANDATORY propagation; an audit persistence failure rolls back the administration
mutation instead of leaving a committed change without its event.

Allowlisted JSON snapshots record role name/base type/system flag/sorted permission
codes, or username/role ID/effective base type/activation. Update events capture
previous and new values; creation/deletion capture the respective one-sided value.
Entities and requests are never serialized. Email, display name, credential hashes,
password inputs, TOTP secrets and recovery codes are excluded from these snapshots.
Existing audit access protection and tenant attribution remain unchanged.

Scoped service lookups supply the previous values. These snapshots do not claim
serializable concurrent-edit protection, nor a complete security-event history;
failed operations are not logged as successful changes. Tests cover mutation
rollback on audit failure and safe, deterministic allowlisted serialization.
No migration or tenant activation is introduced. This stage remains local.

Verification: complete regression ran 354 Java tests with no failures/errors
(two skipped). Administration rollback and allowlist snapshot tests passed;
whitespace validation passed. Not deployed.

## Stage 22: readable administration audit changes

The existing audit dialog renders structured user/role snapshots as a field table
with previous/new values and a changed marker. Supported fields are allowlisted;
unknown fields are not rendered in the structured table. Creation/deletion have
one-sided values. Malformed JSON, old plain-text snapshots and other event types
retain an escaped text fallback. All snapshot content is escaped before markup.

A separate ROLE area filter prevents role changes from appearing as LC events.
Field labels and change controls support English/German. A horizontally scrollable
table and the existing blue palette are used. An external script loads before the
application, preserving the inline-script CSP restriction; cache versions changed.
No backend, SQL or access-control change is introduced.

Verification: all 40 JavaScript tests passed, including safe rendering, highlights,
legacy fallback, translations, role filtering and script order. App syntax and
whitespace validation passed. Visual browser QA was not performed. Not deployed.

## Stage 23: serialize tenant administration decisions

User/role administration acquires a pessimistic write lock on the current tenant
row inside the mutation transaction. Role/user creation, updates and deletion
use the same lock, including standalone service entry points. The audit controllers
acquire it before loading previous values, avoiding stale pre-lock snapshots in
the normal endpoint flow. The lock remains held through the atomic audit commit
or rollback; it serializes competing last-administrator decisions within a tenant.

The lock service requires an existing transaction and tenant. Missing context
records are rejected, not treated as an unlocked fallback. Authentication, OCR,
outbox and read-only lists do not acquire this administration lock. This replaces
the previously documented concurrent-decision limitation for the covered mutation
entry points, not arbitrary database writes or already-running authenticated requests.
No migration or additional tenant activation is introduced.

Tests run two concurrent administration transactions against the same tenant;
exactly one administrator deactivation succeeds and one active administrator remains.
Rollback tests continue to cover audit failure. Additional tests require a transaction
and reject unavailable tenants. Stages 22–23 remain local, not deployed.

Pre-release verification on 2026-10-07: full Java regression passed without
failures/errors, including the parallel administrator test and transaction/tenant
negative tests. All 40 JavaScript tests passed; whitespace validation passed.
