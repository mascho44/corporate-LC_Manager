-- Channels that fetch files into the document inbox on their own (watched folder, later mailbox), per tenant.
CREATE TABLE intake_channel (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 type varchar(20) NOT NULL,
 name varchar(40) NOT NULL,
 enabled boolean NOT NULL DEFAULT false,
 interval_minutes integer NOT NULL DEFAULT 2,
 last_run_at timestamp,
 last_result varchar(500),
 created_by varchar(100) NOT NULL,
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT intake_channel_name UNIQUE (tenant_id, name),
 CONSTRAINT intake_channel_interval CHECK (interval_minutes BETWEEN 1 AND 1440)
);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON intake_channel FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();

-- Removed together with the tenant; the trigger name sorts before tenant_business_purge, so the rows are gone before its final check.
CREATE OR REPLACE FUNCTION purge_deleted_tenant_intake_channel() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN DELETE FROM intake_channel WHERE tenant_id=NEW.id; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_aa_intake_channel_purge AFTER UPDATE OF deleted_at ON tenant FOR EACH ROW EXECUTE FUNCTION purge_deleted_tenant_intake_channel();
