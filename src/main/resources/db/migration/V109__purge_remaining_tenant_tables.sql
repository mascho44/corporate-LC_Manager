-- The tenant purge (V73, V108) left tenant-level tables that were added later; the final check then stopped the purge as soon as one of them had rows.
CREATE OR REPLACE FUNCTION purge_deleted_tenant_business_data() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE tab text; remaining bigint;
BEGIN
 IF OLD.deleted_at IS NOT NULL THEN
  IF NEW.deleted_at IS DISTINCT FROM OLD.deleted_at THEN RAISE EXCEPTION 'Deleted tenant cannot be restored'; END IF;
  RETURN NEW;
 END IF;
 IF NEW.deleted_at IS NULL THEN RETURN NEW; END IF;
 -- Fixed owner-qualified order. FK failures roll back the entire purge.
 DELETE FROM document_inbox_item WHERE tenant_id=NEW.id;
 DELETE FROM training_session WHERE tenant_id=NEW.id;
 DELETE FROM training_learning_control WHERE tenant_id=NEW.id;
 DELETE FROM document_template WHERE tenant_id=NEW.id;
 DELETE FROM charge_estimate WHERE tenant_id=NEW.id;
 DELETE FROM letter_of_credit WHERE tenant_id=NEW.id;
 DELETE FROM company_profile WHERE tenant_id=NEW.id;
 -- Tenant-level settings and data added after V73 (no cascade fires because the tenant row itself stays as a tombstone). Dependants first: workflows and tasks refer to teams.
 FOREACH tab IN ARRAY ARRAY['lc_task','swift_message','workflow','automation_rule','team','ebics_message','ebics_connection','tenant_ai_setting','tenant_scan_profile','document_metadata_training','document_spatial_layout'] LOOP
  EXECUTE format('DELETE FROM %I WHERE tenant_id=$1',tab) USING NEW.id;
 END LOOP;
 DELETE FROM internal_rule_pack WHERE tenant_id=NEW.id;
 DELETE FROM internal_rule_pack_version WHERE tenant_id=NEW.id;
 DELETE FROM platform_invitation WHERE tenant_id=NEW.id;
 DELETE FROM tenant_membership_role_command WHERE tenant_id=NEW.id;
 DELETE FROM tenant_membership_provision_command WHERE tenant_id=NEW.id;
 DELETE FROM tenant_membership WHERE tenant_id=NEW.id;
 DELETE FROM app_role_permission WHERE role_id IN (SELECT id FROM app_role WHERE tenant_id=NEW.id);
 DELETE FROM app_role WHERE tenant_id=NEW.id;
 FOREACH tab IN ARRAY ARRAY['document_approval_threshold','swift_import_record','charge_profile','integration_outbox'] LOOP
  EXECUTE format('DELETE FROM %I WHERE tenant_id=$1',tab) USING NEW.id;
 END LOOP;
 FOR tab IN SELECT table_name FROM information_schema.columns WHERE table_schema='public' AND column_name='tenant_id' AND table_name<>'audit_event' LOOP
  EXECUTE format('SELECT count(*) FROM %I WHERE tenant_id=$1',tab) INTO remaining USING NEW.id;
  IF remaining<>0 THEN RAISE EXCEPTION 'Unresolved tenant data category: %',tab; END IF;
 END LOOP;
 RETURN NEW;
END $$;
