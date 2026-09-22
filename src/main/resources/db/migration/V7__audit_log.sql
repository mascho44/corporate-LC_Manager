create table audit_event (
    id uuid primary key,
    username varchar(100) not null,
    action varchar(80) not null,
    entity_type varchar(80),
    entity_id varchar(255),
    details varchar(2000),
    successful boolean not null,
    ip_address varchar(64),
    occurred_at timestamp not null
);

create index idx_audit_event_occurred_at on audit_event (occurred_at desc);
create index idx_audit_event_username on audit_event (username);
