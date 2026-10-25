ALTER TABLE lc_document ADD COLUMN ocr_evidence_json text;
ALTER TABLE training_session ADD COLUMN ocr_confidence_json text;
