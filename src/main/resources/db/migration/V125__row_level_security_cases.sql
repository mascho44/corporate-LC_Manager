-- Row-Level-Security, Schritt 6: Akten und ihre Kindtabellen. Siehe V121.
ALTER TABLE letter_of_credit ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_amendment ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_task ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_condition ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_additional_field ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_required_document ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON letter_of_credit
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON lc_document
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON lc_amendment
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON lc_task
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- Kindtabellen ohne eigene tenant_id folgen ihrer Akte (deren Richtlinie gilt in der Unterabfrage mit).
CREATE POLICY tenant_isolation ON lc_condition
 USING (EXISTS (SELECT 1 FROM letter_of_credit l WHERE l.id = lc_condition.lc_id))
 WITH CHECK (EXISTS (SELECT 1 FROM letter_of_credit l WHERE l.id = lc_condition.lc_id));
CREATE POLICY tenant_isolation ON lc_additional_field
 USING (EXISTS (SELECT 1 FROM letter_of_credit l WHERE l.id = lc_additional_field.lc_id))
 WITH CHECK (EXISTS (SELECT 1 FROM letter_of_credit l WHERE l.id = lc_additional_field.lc_id));
CREATE POLICY tenant_isolation ON lc_required_document
 USING (EXISTS (SELECT 1 FROM letter_of_credit l WHERE l.id = lc_required_document.lc_id))
 WITH CHECK (EXISTS (SELECT 1 FROM letter_of_credit l WHERE l.id = lc_required_document.lc_id));
