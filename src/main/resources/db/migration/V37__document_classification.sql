ALTER TABLE lc_document ADD COLUMN classification_history_json text;
ALTER TABLE document_inbox_item ADD COLUMN classification_history_json text;
ALTER TABLE document_inbox_item ADD COLUMN ocr_evidence_json text;
