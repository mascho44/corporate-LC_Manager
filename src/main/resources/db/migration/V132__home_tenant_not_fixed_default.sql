-- Der Standardmandant 'default' ist nicht mehr fest geschuetzt: Geschuetzt ist der jeweilige Heimatmandant (platform_home_tenant).
ALTER TABLE tenant DROP CONSTRAINT IF EXISTS tenant_default_not_archived;
ALTER TABLE tenant DROP CONSTRAINT IF EXISTS tenant_deleted_inactive;
ALTER TABLE tenant ADD CONSTRAINT tenant_deleted_inactive CHECK (deleted_at IS NULL OR (active = false AND archived_at IS NOT NULL));

CREATE FUNCTION protect_home_tenant() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.archived_at IS NOT NULL OR NEW.deleted_at IS NOT NULL OR NEW.active = false)
    AND EXISTS (SELECT 1 FROM platform_home_tenant WHERE tenant_id = NEW.id) THEN
  RAISE EXCEPTION 'Der Heimatmandant kann nicht gesperrt, archiviert oder geloescht werden. Zuerst einen anderen Heimatmandanten festlegen.';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_home_protection BEFORE UPDATE ON tenant FOR EACH ROW EXECUTE FUNCTION protect_home_tenant();
