-- Woher eine abgelegte Bankmitteilung kommt: EBICS-Abruf oder SWIFT-Ordner (Datei aus dem Eingangsordner); origin nennt die Datei.
ALTER TABLE ebics_message ADD COLUMN source varchar(10) NOT NULL DEFAULT 'EBICS';
ALTER TABLE ebics_message ADD COLUMN origin varchar(200);
ALTER TABLE ebics_message ADD CONSTRAINT ebics_message_source CHECK (source IN ('EBICS','FOLDER'));
