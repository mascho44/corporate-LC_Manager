-- Controlled removal of a membership in a shared tenant (same pattern as role updates): the direct-write guard of tenant_membership stays,
-- the delete happens inside the trigger of a transient command row. The home membership of an identity cannot be removed this way.
CREATE TABLE tenant_membership_remove_command (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 user_id uuid NOT NULL REFERENCES app_user(id)
);
REVOKE ALL ON tenant_membership_remove_command FROM PUBLIC;

CREATE FUNCTION execute_membership_remove_command() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE identity_home uuid;
BEGIN
 SELECT tenant_id INTO identity_home FROM app_user WHERE id=NEW.user_id FOR UPDATE;
 IF identity_home IS NULL OR identity_home=NEW.tenant_id THEN
  RAISE EXCEPTION 'Home membership remains managed by user administration';
 END IF;
 DELETE FROM tenant_membership WHERE tenant_id=NEW.tenant_id AND user_id=NEW.user_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Membership not found'; END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION execute_membership_remove_command() FROM PUBLIC;
CREATE TRIGGER tenant_membership_remove_execute AFTER INSERT
 ON tenant_membership_remove_command FOR EACH ROW
 EXECUTE FUNCTION execute_membership_remove_command();

CREATE FUNCTION remove_tenant_membership(target_tenant uuid,target_user uuid)
 RETURNS void LANGUAGE plpgsql AS $$
DECLARE command_id uuid=gen_random_uuid();
BEGIN
 INSERT INTO tenant_membership_remove_command(id,tenant_id,user_id) VALUES(command_id,target_tenant,target_user);
 DELETE FROM tenant_membership_remove_command WHERE id=command_id;
END $$;
REVOKE ALL ON FUNCTION remove_tenant_membership(uuid,uuid) FROM PUBLIC;
