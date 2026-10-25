-- Row-Level-Security, Schritt 5: Stammdaten, Beschaffung, Gebuehren, Vorlagen. Siehe V121.
ALTER TABLE customer ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbox_customer_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement_item_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement_lead_time ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement_digest_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE charge_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE charge_estimate ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE company_profile ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON inbox_customer_assignment
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON procurement_item_status
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON procurement_lead_time
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON procurement_digest_log
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON charge_profile
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON charge_estimate
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON document_template
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON company_profile
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- Die grosse Loeschfunktion fuer Mandantendaten muss unabhaengig vom Kontext alle Zeilen sehen (auch fuer die folgenden RLS-Schritte).
ALTER FUNCTION purge_deleted_tenant_business_data() SECURITY DEFINER SET search_path = public;
