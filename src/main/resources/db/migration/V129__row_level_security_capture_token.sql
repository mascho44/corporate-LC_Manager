-- Row-Level-Security, Schritt 10: Erfassungszugaenge (Scanner-Token-Hashes). Siehe V121.
-- Die Token-Suche vor der Mandantenbestimmung laeuft im ausdruecklichen Plattform-Scope (app.platform = 'on').
ALTER TABLE capture_token ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON capture_token
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid OR current_setting('app.platform', true) = 'on')
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid OR current_setting('app.platform', true) = 'on');
