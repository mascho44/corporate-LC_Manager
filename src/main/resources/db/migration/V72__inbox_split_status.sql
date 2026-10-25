-- V48 introduced split lineage but did not extend the V32 status constraint.
-- Both manual and automatic splits retain their source with status SPLIT.
ALTER TABLE document_inbox_item DROP CONSTRAINT chk_document_inbox_status;
ALTER TABLE document_inbox_item ADD CONSTRAINT chk_document_inbox_status
 CHECK (status IN ('OPEN','ATTACHED','SPLIT'));
