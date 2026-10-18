-- Heimatmandant: der Mandant, in dem die globalen Benutzerkonten und die Plattform-Anmeldung liegen.
-- Bisher fest 'default'; ab jetzt konfigurierbar (genau ein Datensatz). Das Verhalten bleibt unveraendert, solange er auf 'default' steht.
CREATE TABLE platform_home_tenant (
 singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
 tenant_id uuid NOT NULL REFERENCES tenant(id)
);
INSERT INTO platform_home_tenant (singleton, tenant_id) VALUES (true, '00000000-0000-0000-0000-000000000001');
