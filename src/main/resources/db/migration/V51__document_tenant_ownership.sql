-- Backfill from the parent LC, not from a session or a client-supplied tenant.
ALTER TABLE lc_document ADD COLUMN tenant_id uuid;
UPDATE lc_document d SET tenant_id=lc.tenant_id FROM letter_of_credit lc WHERE d.lc_id=lc.id;
ALTER TABLE lc_document ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE lc_document ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE lc_document ADD CONSTRAINT lc_document_tenant_fk FOREIGN KEY(tenant_id) REFERENCES tenant(id);
ALTER TABLE letter_of_credit ADD CONSTRAINT letter_of_credit_tenant_id_unique UNIQUE(tenant_id,id);
ALTER TABLE lc_document ADD CONSTRAINT lc_document_lc_same_tenant FOREIGN KEY(tenant_id,lc_id) REFERENCES letter_of_credit(tenant_id,id) ON DELETE CASCADE;
CREATE INDEX lc_document_tenant_lc_idx ON lc_document(tenant_id,lc_id);
-- Single-tenant activation gate remains unchanged.
