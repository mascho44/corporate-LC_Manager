-- Details a person enters when splitting a collective PDF: the document date (if it was not recognised) and a confirmed signature.
ALTER TABLE document_inbox_item ADD COLUMN manual_document_date date;
ALTER TABLE document_inbox_item ADD COLUMN signature_confirmed boolean NOT NULL DEFAULT false;
