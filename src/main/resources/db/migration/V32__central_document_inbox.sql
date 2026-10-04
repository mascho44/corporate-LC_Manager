create table document_inbox_item (
    id uuid primary key,
    original_filename varchar(255) not null,
    content_type varchar(150) not null,
    file_size bigint not null,
    content bytea,
    status varchar(20) not null default 'OPEN',
    received_by varchar(100) not null,
    received_at timestamp not null,
    extraction_status varchar(30) not null,
    extracted_reference varchar(100),
    extracted_document_number varchar(100),
    extracted_text text,
    extracted_amount numeric(19,2),
    extracted_currency varchar(3),
    attached_lc_id uuid references letter_of_credit(id) on delete set null,
    attached_document_id uuid references lc_document(id) on delete set null,
    constraint chk_document_inbox_status check (status in ('OPEN', 'ATTACHED'))
);

create index idx_document_inbox_open_received on document_inbox_item(status, received_at desc);
