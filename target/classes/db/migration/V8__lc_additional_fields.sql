create table lc_additional_field (
    lc_id uuid not null references letter_of_credit(id) on delete cascade,
    field_name varchar(255) not null,
    field_value varchar(4000),
    primary key (lc_id, field_name)
);
