-- Pruefabschluss: Je Akte ein Abschluss, der bis zu einer Betragsgrenze von einer Person, darueber von mehreren verschiedenen Personen vorgenommen wird.
CREATE TABLE review_close_threshold (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 currency varchar(3) NOT NULL,
 minimum_amount numeric(19,2) NOT NULL,
 required_closers integer NOT NULL,
 CONSTRAINT review_close_threshold_unique UNIQUE (tenant_id, currency, minimum_amount),
 CONSTRAINT review_close_threshold_count CHECK (required_closers BETWEEN 1 AND 5)
);
CREATE TABLE lc_review_closure (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 lc_id uuid NOT NULL REFERENCES letter_of_credit(id) ON DELETE CASCADE,
 status varchar(10) NOT NULL,
 required_closers integer NOT NULL,
 summary varchar(1900) NOT NULL,
 started_by varchar(100) NOT NULL,
 signers varchar(600) NOT NULL,
 started_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at timestamp,
 CONSTRAINT lc_review_closure_lc UNIQUE (tenant_id, lc_id),
 CONSTRAINT lc_review_closure_status CHECK (status IN ('PENDING','COMPLETED'))
);
ALTER TABLE review_close_threshold ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON review_close_threshold
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
ALTER TABLE lc_review_closure ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON lc_review_closure
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON review_close_threshold FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON lc_review_closure FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();

-- Mit dem Mandanten entfernt (Trigger-Name sortiert vor tenant_business_purge).
CREATE OR REPLACE FUNCTION purge_deleted_tenant_review_closure() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
  DELETE FROM lc_review_closure WHERE tenant_id=NEW.id;
  DELETE FROM review_close_threshold WHERE tenant_id=NEW.id;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_aa_review_closure_purge AFTER UPDATE OF deleted_at ON tenant FOR EACH ROW EXECUTE FUNCTION purge_deleted_tenant_review_closure();
