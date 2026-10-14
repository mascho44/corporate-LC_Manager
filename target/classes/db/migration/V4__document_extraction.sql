alter table lc_document add column extracted_reference varchar(100);
alter table lc_document add column extracted_document_number varchar(100);
alter table lc_document add column extracted_amount numeric(19,2);
alter table lc_document add column extracted_currency varchar(3);
alter table lc_document add column extraction_status varchar(30) not null default 'NOT_PROCESSED';
alter table lc_document add column extracted_text text;
