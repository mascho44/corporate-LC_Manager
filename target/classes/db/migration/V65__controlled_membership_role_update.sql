-- Internal invoker-rights preparation only. Preserve bootstrap and direct-write guards.
CREATE TABLE tenant_membership_role_command (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 user_id uuid NOT NULL REFERENCES app_user(id),
 role_id uuid NOT NULL,
 FOREIGN KEY(tenant_id,role_id) REFERENCES app_role(tenant_id,id)
);
REVOKE ALL ON tenant_membership_role_command FROM PUBLIC;

CREATE FUNCTION execute_membership_role_command() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE identity_home uuid;
BEGIN
 SELECT tenant_id INTO identity_home FROM app_user WHERE id=NEW.user_id FOR UPDATE;
 IF identity_home IS NULL OR identity_home=NEW.tenant_id THEN
  RAISE EXCEPTION 'Home membership remains managed by user administration';
 END IF;
 UPDATE tenant_membership SET role_id=NEW.role_id
  WHERE tenant_id=NEW.tenant_id AND user_id=NEW.user_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Membership not found'; END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION execute_membership_role_command() FROM PUBLIC;
CREATE TRIGGER tenant_membership_role_execute AFTER INSERT
 ON tenant_membership_role_command FOR EACH ROW
 EXECUTE FUNCTION execute_membership_role_command();

CREATE FUNCTION update_tenant_membership_role(target_tenant uuid,target_user uuid,target_role uuid)
 RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE command_id uuid=gen_random_uuid(); membership_id uuid;
BEGIN
 INSERT INTO tenant_membership_role_command(id,tenant_id,user_id,role_id)
  VALUES(command_id,target_tenant,target_user,target_role);
 DELETE FROM tenant_membership_role_command WHERE id=command_id;
 SELECT id INTO membership_id FROM tenant_membership
  WHERE tenant_id=target_tenant AND user_id=target_user;
 RETURN membership_id;
END $$;
REVOKE ALL ON FUNCTION update_tenant_membership_role(uuid,uuid,uuid) FROM PUBLIC;
