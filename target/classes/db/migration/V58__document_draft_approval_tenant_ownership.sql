ALTER TABLE document_draft ADD COLUMN tenant_id uuid;
UPDATE document_draft d SET tenant_id=lc.tenant_id FROM letter_of_credit lc WHERE lc.id=d.lc_id;
ALTER TABLE document_draft ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE document_draft ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE document_draft ADD CONSTRAINT document_draft_tenant_fk FOREIGN KEY(tenant_id) REFERENCES tenant(id);
ALTER TABLE document_draft ADD CONSTRAINT document_draft_lc_same_tenant
 FOREIGN KEY(tenant_id,lc_id) REFERENCES letter_of_credit(tenant_id,id) ON DELETE CASCADE;
CREATE INDEX document_draft_tenant_lc_updated_idx ON document_draft(tenant_id,lc_id,updated_at DESC);
ALTER TABLE document_approval_threshold ADD COLUMN tenant_id uuid NOT NULL
 DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES tenant(id);
ALTER TABLE document_approval_threshold DROP CONSTRAINT uq_document_approval_threshold;
ALTER TABLE document_approval_threshold ADD CONSTRAINT uq_document_approval_threshold
 UNIQUE(tenant_id,currency,minimum_amount);
