create table document_draft (
    id uuid primary key,
    lc_id uuid not null references letter_of_credit(id) on delete cascade,
    document_type varchar(50) not null,
    document_number varchar(100) not null,
    status varchar(20) not null,
    data_json text not null,
    version bigint not null default 0,
    created_by varchar(100) not null,
    updated_by varchar(100) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
create index idx_document_draft_lc on document_draft(lc_id, updated_at desc);
