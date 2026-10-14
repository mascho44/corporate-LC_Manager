-- Where a person found the evidence for a manual confirmation: a rectangle on a page of a document of the file (JSON: documentId, page, x, y, w, h as fractions of the page).
ALTER TABLE document_check_decision ADD COLUMN evidence_mark text;
