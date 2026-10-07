-- Explicitly authorized multi-tenant activation; access comes from verified session memberships.
-- Direct membership write guards and same-tenant role foreign keys remain unchanged.
ALTER TABLE tenant DROP CONSTRAINT tenant_bootstrap_single;
CREATE UNIQUE INDEX tenant_code_casefold_unique ON tenant(lower(code));
