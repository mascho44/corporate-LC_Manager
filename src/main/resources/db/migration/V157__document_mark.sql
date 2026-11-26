-- Markierungen auf Dokumentseiten (Stempel, Unterschrift, unterschriebener Stempel, weder noch): Entscheidungen von Menschen, spaeter Trainingsdaten fuer Vorschlaege je Mandant.
CREATE TABLE lc_document_mark (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 document_id uuid NOT NULL REFERENCES lc_document(id) ON DELETE CASCADE,
 page integer NOT NULL CHECK (page >= 1),
 box_x double precision NOT NULL CHECK (box_x >= 0 AND box_x < 1),
 box_y double precision NOT NULL CHECK (box_y >= 0 AND box_y < 1),
 box_w double precision NOT NULL CHECK (box_w > 0 AND box_w <= 1),
 box_h double precision NOT NULL CHECK (box_h > 0 AND box_h <= 1),
 kind varchar(16) NOT NULL CHECK (kind IN ('STAMP','SIGNATURE','SIGNED_STAMP','NONE')),
 marked_by varchar(100) NOT NULL,
 marked_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX lc_document_mark_document ON lc_document_mark (tenant_id, document_id);
ALTER TABLE lc_document_mark ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON lc_document_mark
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON lc_document_mark FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();
CREATE OR REPLACE FUNCTION purge_deleted_tenant_lc_document_mark() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN DELETE FROM lc_document_mark WHERE tenant_id=NEW.id; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_aa_lc_document_mark_purge AFTER UPDATE OF deleted_at ON tenant FOR EACH ROW EXECUTE FUNCTION purge_deleted_tenant_lc_document_mark();
