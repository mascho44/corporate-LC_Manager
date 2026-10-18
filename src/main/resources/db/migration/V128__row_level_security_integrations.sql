-- Row-Level-Security, Schritt 9: Anbindungen (EBICS, SWIFT, Outbox, E-Mail) und interne Regelpakete. Siehe V121.
-- capture_token bleibt bewusst aussen vor: der Erfassungszugang wird vor der Mandantenbestimmung per Token gesucht.
ALTER TABLE ebics_connection ENABLE ROW LEVEL SECURITY;
ALTER TABLE ebics_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE swift_import_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE swift_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal_rule_pack ENABLE ROW LEVEL SECURITY;
ALTER TABLE internal_rule_pack_version ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ebics_connection
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON ebics_message
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON swift_import_record
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON swift_message
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON integration_outbox
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON email_delivery
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON internal_rule_pack
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON internal_rule_pack_version
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
