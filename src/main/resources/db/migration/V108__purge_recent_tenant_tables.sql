-- Tables added after the tenant purge (V73) must be emptied when a tenant is deleted, otherwise the purge stops with "Unresolved tenant data category".
CREATE FUNCTION purge_recent_tenant_tables() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE tab text;
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
  -- Dependants first: assignments and capture infos refer to inbox items, procurement statuses to LC files.
  FOREACH tab IN ARRAY ARRAY['inbox_customer_assignment','inbox_capture_info','customer','capture_token','procurement_item_status','procurement_lead_time','procurement_digest_log','document_type_example'] LOOP
   EXECUTE format('DELETE FROM %I WHERE tenant_id=$1',tab) USING NEW.id;
  END LOOP;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tenant_recent_tables_purge BEFORE UPDATE OF deleted_at ON tenant
 FOR EACH ROW EXECUTE FUNCTION purge_recent_tenant_tables();
