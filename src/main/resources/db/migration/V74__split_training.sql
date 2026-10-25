-- Confirmed split patterns retain hashes and page/type boundaries, never PDF bytes or OCR text.
CREATE TABLE document_split_training (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 pattern_hash varchar(64) NOT NULL,
 parts_json text NOT NULL,
 confirmed_by varchar(255) NOT NULL,
 confirmed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX document_split_training_lookup ON document_split_training(tenant_id,pattern_hash);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON document_split_training
 FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();
CREATE FUNCTION purge_split_training() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
  DELETE FROM document_split_training WHERE tenant_id=NEW.id;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_split_training_purge BEFORE UPDATE OF deleted_at ON tenant
 FOR EACH ROW EXECUTE FUNCTION purge_split_training();
