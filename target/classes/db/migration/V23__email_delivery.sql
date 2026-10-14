create table email_delivery (
    id uuid primary key,
    lc_id uuid not null references letter_of_credit(id) on delete cascade,
    recipients text not null,
    subject varchar(500) not null,
    attachment_names text,
    status varchar(20) not null,
    error_message varchar(1000),
    sent_by varchar(100) not null,
    sent_at timestamp not null
);
create index idx_email_delivery_lc on email_delivery(lc_id, sent_at desc);
insert into app_role_permission(role_id,permission) values
('00000000-0000-0000-0000-000000000001','EMAIL_SEND'),
('00000000-0000-0000-0000-000000000002','EMAIL_SEND');
