-- Papierkorb: geloeschte Dokumente werden markiert und nach 30 Tagen endgueltig entfernt.
ALTER TABLE lc_document ADD COLUMN deleted_at TIMESTAMP;
CREATE INDEX idx_lc_document_deleted ON lc_document (tenant_id, deleted_at) WHERE deleted_at IS NOT NULL;
