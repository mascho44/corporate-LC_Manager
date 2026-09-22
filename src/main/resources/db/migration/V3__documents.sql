create table lc_document (
    id uuid primary key,
    lc_id uuid not null references letter_of_credit(id) on delete cascade,
    document_type varchar(50) not null,
    original_filename varchar(255) not null,
    content_type varchar(255) not null,
    file_size bigint not null,
    document_date date,
    amount numeric(19,2),
    currency varchar(3),
    content bytea not null,
    uploaded_at timestamp not null
);
create index idx_lc_document_lc on lc_document(lc_id);
