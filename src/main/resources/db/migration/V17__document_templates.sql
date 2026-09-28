create table document_template (
    id uuid primary key,
    document_type varchar(60) not null unique,
    original_filename varchar(255) not null,
    content_type varchar(150) not null,
    file_size bigint not null,
    content bytea not null,
    uploaded_by varchar(100) not null,
    uploaded_at timestamp not null
);
