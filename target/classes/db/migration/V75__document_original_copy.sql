ALTER TABLE lc_document ADD COLUMN copy_number integer CHECK(copy_number BETWEEN 0 AND 3);
ALTER TABLE document_inbox_item ADD COLUMN copy_number integer CHECK(copy_number BETWEEN 0 AND 3);
-- Existing documents deliberately remain unspecified; never infer authenticity from filenames.
