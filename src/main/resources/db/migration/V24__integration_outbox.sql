create table integration_outbox (
    id uuid primary key,
    topic varchar(150) not null,
    message_key varchar(255),
    payload text not null,
    status varchar(20) not null,
    attempts integer not null default 0,
    next_attempt_at timestamp not null,
    created_at timestamp not null,
    published_at timestamp,
    last_error varchar(1000)
);
create index idx_integration_outbox_pending on integration_outbox(status, next_attempt_at, created_at);
