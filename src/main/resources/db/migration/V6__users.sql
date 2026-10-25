create table app_user (
    id uuid primary key,
    username varchar(100) not null unique,
    display_name varchar(255) not null,
    password_hash varchar(255) not null,
    role varchar(30) not null,
    active boolean not null default true,
    created_at timestamp not null
);
