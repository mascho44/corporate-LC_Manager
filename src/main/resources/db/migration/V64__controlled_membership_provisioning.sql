-- Internal database-owner-only foundation. No API or tenant switch is enabled.
-- Preserve the single-tenant constraint and all direct membership write guards.
CREATE TABLE tenant_membership_provision_command (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 user_id uuid NOT NULL REFERENCES app_user(id),
 role_id uuid NOT NULL,
 FOREIGN KEY(tenant_id,role_id) REFERENCES app_role(tenant_id,id)
);
REVOKE ALL ON tenant_membership_provision_command FROM PUBLIC;

CREATE FUNCTION execute_membership_provision_command() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE identity_home uuid; identity_active boolean;
BEGIN
 SELECT tenant_id,active INTO identity_home,identity_active FROM app_user
  WHERE id=NEW.user_id FOR UPDATE;
 IF identity_home IS NULL OR NOT identity_active THEN
  RAISE EXCEPTION 'An active existing identity is required';
 END IF;
 IF identity_home=NEW.tenant_id THEN
  RAISE EXCEPTION 'Home membership remains managed by user administration';
 END IF;
 INSERT INTO tenant_membership(id,tenant_id,user_id,role_id,active)
  VALUES(NEW.id,NEW.tenant_id,NEW.user_id,NEW.role_id,true);
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION execute_membership_provision_command() FROM PUBLIC;
CREATE TRIGGER tenant_membership_provision_execute AFTER INSERT
 ON tenant_membership_provision_command FOR EACH ROW
 EXECUTE FUNCTION execute_membership_provision_command();

CREATE FUNCTION provision_tenant_membership(target_tenant uuid,target_user uuid,target_role uuid)
 RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE membership_id uuid=gen_random_uuid();
BEGIN
 INSERT INTO tenant_membership_provision_command(id,tenant_id,user_id,role_id)
  VALUES(membership_id,target_tenant,target_user,target_role);
 DELETE FROM tenant_membership_provision_command WHERE id=membership_id;
 RETURN membership_id;
END $$;
REVOKE ALL ON FUNCTION provision_tenant_membership(uuid,uuid,uuid) FROM PUBLIC;
