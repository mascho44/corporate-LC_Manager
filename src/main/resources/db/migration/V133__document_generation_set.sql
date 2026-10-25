-- Erzeugte Dokumentensaetze: gemeinsame Kennung und die Eingabe, damit ein Satz korrigiert neu erzeugt werden kann.
ALTER TABLE lc_document ADD COLUMN generation_set_id UUID;
ALTER TABLE lc_document ADD COLUMN generation_input TEXT;
CREATE INDEX idx_lc_document_generation_set ON lc_document (generation_set_id);
