-- Trainingsdokumente fuer Stempel/Unterschriften: PDFs ohne LC-Akte, nur zum Markieren und Lernen, je Mandant.
CREATE TABLE mark_training_document (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 filename varchar(255) NOT NULL,
 file_size bigint NOT NULL,
 page_count integer NOT NULL CHECK (page_count >= 1),
 content bytea NOT NULL,
 uploaded_by varchar(100) NOT NULL,
 uploaded_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX mark_training_document_tenant ON mark_training_document (tenant_id, uploaded_at DESC);
ALTER TABLE mark_training_document ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON mark_training_document
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON mark_training_document FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();
CREATE OR REPLACE FUNCTION purge_deleted_tenant_mark_training_document() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN DELETE FROM mark_training_document WHERE tenant_id=NEW.id; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_ab_mark_training_document_purge AFTER UPDATE OF deleted_at ON tenant FOR EACH ROW EXECUTE FUNCTION purge_deleted_tenant_mark_training_document();

-- Eine Markierung gehoert entweder zu einem Dokument einer LC-Akte oder zu einem Trainingsdokument.
ALTER TABLE lc_document_mark ALTER COLUMN document_id DROP NOT NULL;
ALTER TABLE lc_document_mark ADD COLUMN training_document_id uuid REFERENCES mark_training_document(id) ON DELETE CASCADE;
ALTER TABLE lc_document_mark ADD CONSTRAINT lc_document_mark_one_source CHECK ((document_id IS NULL) <> (training_document_id IS NULL));
CREATE INDEX lc_document_mark_training ON lc_document_mark (tenant_id, training_document_id);
