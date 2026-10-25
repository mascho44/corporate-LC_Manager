-- Progress of the guided examination (Prüfungsassistent) per file: which step was completed or skipped (with a reason), by whom and when.
CREATE TABLE lc_review_step (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 lc_id uuid NOT NULL REFERENCES letter_of_credit(id) ON DELETE CASCADE,
 step_key varchar(30) NOT NULL,
 status varchar(10) NOT NULL,
 reason varchar(500),
 completed_by varchar(100) NOT NULL,
 completed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT lc_review_step_key UNIQUE (tenant_id, lc_id, step_key),
 CONSTRAINT lc_review_step_status CHECK (status IN ('DONE','SKIPPED'))
);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON lc_review_step FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();

-- Removed together with the tenant; the trigger name sorts before tenant_business_purge, so the rows are gone before its final check.
CREATE OR REPLACE FUNCTION purge_deleted_tenant_lc_review_step() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN DELETE FROM lc_review_step WHERE tenant_id=NEW.id; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_aa_lc_review_step_purge AFTER UPDATE OF deleted_at ON tenant FOR EACH ROW EXECUTE FUNCTION purge_deleted_tenant_lc_review_step();
