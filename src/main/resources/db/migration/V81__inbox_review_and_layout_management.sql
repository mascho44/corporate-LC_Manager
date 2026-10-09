ALTER TABLE document_inbox_item ADD COLUMN metadata_review_json text;
ALTER TABLE document_spatial_layout ADD COLUMN active boolean NOT NULL DEFAULT true;
