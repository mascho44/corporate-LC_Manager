-- Row-Level-Security, Schritt 8: Workflows, Teams, Automatisierung und Mandanten-Einstellungen. Siehe V121.
ALTER TABLE workflow ENABLE ROW LEVEL SECURITY;
ALTER TABLE team ENABLE ROW LEVEL SECURITY;
ALTER TABLE automation_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_approval_threshold ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_scan_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_ai_setting ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_member ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON workflow
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON team
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON automation_rule
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON document_approval_threshold
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON tenant_scan_profile
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON tenant_ai_setting
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- team_member hat keine eigene tenant_id und folgt seinem Team.
CREATE POLICY tenant_isolation ON team_member
 USING (EXISTS (SELECT 1 FROM team t WHERE t.id = team_member.team_id))
 WITH CHECK (EXISTS (SELECT 1 FROM team t WHERE t.id = team_member.team_id));

-- Fehlte in V126: Die Loeschfunktion des Split-Trainings (V74) muss kontextunabhaengig loeschen koennen.
ALTER FUNCTION purge_split_training() SECURITY DEFINER SET search_path = public;
