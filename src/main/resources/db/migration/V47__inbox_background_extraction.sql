ALTER TABLE document_inbox_item ADD COLUMN extraction_started_at timestamp;
ALTER TABLE document_inbox_item ADD COLUMN extraction_token uuid;
CREATE INDEX idx_inbox_extraction_queue ON document_inbox_item(status, extraction_status, received_at);
