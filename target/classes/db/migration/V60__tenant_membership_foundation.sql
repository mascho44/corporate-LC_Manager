-- Compatibility foundation only: do not remove tenant_bootstrap_single.
CREATE TABLE tenant_membership (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 role_id uuid NOT NULL,
 active boolean NOT NULL,
 CONSTRAINT tenant_membership_identity UNIQUE(tenant_id,user_id),
 CONSTRAINT tenant_membership_role_same_tenant FOREIGN KEY(tenant_id,role_id) REFERENCES app_role(tenant_id,id)
);
INSERT INTO tenant_membership(tenant_id,user_id,role_id,active)
 SELECT tenant_id,id,assigned_role_id,active FROM app_user;

-- One existing write authority avoids divergent role/activation data while migrating.
CREATE FUNCTION sync_bootstrap_tenant_membership() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO tenant_membership(tenant_id,user_id,role_id,active)
 VALUES(NEW.tenant_id,NEW.id,NEW.assigned_role_id,NEW.active)
 ON CONFLICT(tenant_id,user_id) DO UPDATE SET role_id=EXCLUDED.role_id,active=EXCLUDED.active;
 RETURN NEW;
END $$;
CREATE TRIGGER app_user_membership_sync AFTER INSERT OR UPDATE OF assigned_role_id,active
 ON app_user FOR EACH ROW EXECUTE FUNCTION sync_bootstrap_tenant_membership();

-- Allow the user trigger and FK cascades, not a second direct write path.
CREATE FUNCTION protect_bootstrap_tenant_membership() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF pg_trigger_depth() < 2 THEN
  RAISE EXCEPTION 'Memberships are maintained by user administration during bootstrap';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_membership_write_guard BEFORE INSERT OR UPDATE OR DELETE
 ON tenant_membership FOR EACH ROW EXECUTE FUNCTION protect_bootstrap_tenant_membership();
CREATE TRIGGER tenant_membership_truncate_guard BEFORE TRUNCATE
 ON tenant_membership FOR EACH STATEMENT EXECUTE FUNCTION protect_bootstrap_tenant_membership();
