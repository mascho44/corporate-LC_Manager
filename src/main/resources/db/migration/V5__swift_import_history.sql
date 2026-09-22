create table swift_import_record (id uuid primary key, filename varchar(255) not null, message_type varchar(20), reference varchar(255), status varchar(30) not null, message varchar(2000), imported_at timestamp not null);
create index idx_swift_import_record_date on swift_import_record(imported_at desc);
