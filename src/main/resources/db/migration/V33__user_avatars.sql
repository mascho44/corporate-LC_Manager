create table user_avatar (
    user_id uuid primary key references app_user(id) on delete cascade,
    content bytea not null,
    updated_at timestamp with time zone not null
);
