-- Entwurf eines Akkreditivs zur Akte (Bank): der gepruefte Entwurf wird hinterlegt, damit die spaeter eingegangene LC damit abgeglichen werden kann.
CREATE TABLE lc_draft (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 lc_id uuid NOT NULL REFERENCES letter_of_credit(id) ON DELETE CASCADE,
 filename varchar(255),
 content text NOT NULL,
 saved_by varchar(100) NOT NULL,
 saved_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT lc_draft_lc UNIQUE (tenant_id, lc_id)
);
ALTER TABLE lc_draft ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON lc_draft
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON lc_draft FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();
CREATE OR REPLACE FUNCTION purge_deleted_tenant_lc_draft() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN DELETE FROM lc_draft WHERE tenant_id=NEW.id; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_aa_lc_draft_purge AFTER UPDATE OF deleted_at ON tenant FOR EACH ROW EXECUTE FUNCTION purge_deleted_tenant_lc_draft();
