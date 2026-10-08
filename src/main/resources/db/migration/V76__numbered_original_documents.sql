-- Preserve legacy 0=Original and 1..3=Copy. Negative values designate numbered originals.
ALTER TABLE lc_document DROP CONSTRAINT lc_document_copy_number_check;
ALTER TABLE lc_document ADD CONSTRAINT lc_document_copy_number_check CHECK(copy_number BETWEEN -3 AND 3);
ALTER TABLE document_inbox_item DROP CONSTRAINT document_inbox_item_copy_number_check;
ALTER TABLE document_inbox_item ADD CONSTRAINT document_inbox_item_copy_number_check CHECK(copy_number BETWEEN -3 AND 3);
