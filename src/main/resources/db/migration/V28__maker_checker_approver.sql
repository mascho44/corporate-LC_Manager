alter table document_draft add column checked_by varchar(100);
alter table document_draft add column checked_at timestamp;

-- The former REVIEWED state meant "submitted for approval", not an independent check.
update document_draft set status = 'SUBMITTED' where status = 'REVIEWED';
