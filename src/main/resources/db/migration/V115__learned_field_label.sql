-- Labels learned from manual entries: under which printed label ("Description of goods:") a document type carries a check field, per tenant.
CREATE TABLE learned_field_label (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 document_type varchar(60) NOT NULL,
 field varchar(80) NOT NULL,
 label varchar(60) NOT NULL,
 learned_by varchar(255) NOT NULL,
 learned_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX learned_field_label_key ON learned_field_label (tenant_id, document_type, field, lower(label));
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON learned_field_label FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();

-- Removed together with the tenant; the trigger name sorts before tenant_business_purge, so the rows are gone before its final check.
CREATE OR REPLACE FUNCTION purge_deleted_tenant_learned_field_label() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN DELETE FROM learned_field_label WHERE tenant_id=NEW.id; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_aa_learned_field_label_purge AFTER UPDATE OF deleted_at ON tenant FOR EACH ROW EXECUTE FUNCTION purge_deleted_tenant_learned_field_label();
