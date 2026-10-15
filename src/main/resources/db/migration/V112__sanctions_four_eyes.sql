-- Sanctions hits: a "false positive" decision needs the approval of a second person (four-eyes principle).
ALTER TABLE document_check_decision ADD COLUMN second_reviewed_by varchar(100);
ALTER TABLE document_check_decision ADD COLUMN second_reviewed_at timestamp;

-- Approved false positives per tenant: the same party against the same, unchanged list entry is not reported again.
CREATE TABLE sanctions_clearance (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    party_key varchar(500) NOT NULL,
    party_value varchar(500) NOT NULL,
    source varchar(20) NOT NULL,
    reference varchar(100) NOT NULL,
    entry_hash varchar(64) NOT NULL,
    listed_name varchar(500) NOT NULL,
    lc_id uuid,
    cleared_by varchar(100) NOT NULL,
    approved_by varchar(100) NOT NULL,
    cleared_at timestamp NOT NULL
);
CREATE UNIQUE INDEX sanctions_clearance_key ON sanctions_clearance (tenant_id, party_key, source, reference, entry_hash);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON sanctions_clearance FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();

-- Removed together with the tenant; the trigger name sorts before tenant_business_purge, so the rows are gone before its final check.
CREATE OR REPLACE FUNCTION purge_deleted_tenant_sanctions_clearance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN DELETE FROM sanctions_clearance WHERE tenant_id=NEW.id; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_aa_sanctions_clearance_purge AFTER UPDATE OF deleted_at ON tenant FOR EACH ROW EXECUTE FUNCTION purge_deleted_tenant_sanctions_clearance();

-- New permission for the second approval; administrators get it.
INSERT INTO app_role_permission (role_id, permission)
SELECT id, 'SANCTIONS_REVIEW' FROM app_role WHERE base_role = 'ADMIN'
ON CONFLICT (role_id, permission) DO NOTHING;
