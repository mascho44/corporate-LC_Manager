ALTER TABLE document_inbox_item ADD COLUMN source_inbox_id uuid;
ALTER TABLE document_inbox_item ADD COLUMN source_from_page integer;
ALTER TABLE document_inbox_item ADD COLUMN source_to_page integer;
