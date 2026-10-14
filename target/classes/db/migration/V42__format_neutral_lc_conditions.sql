create table lc_condition (
 lc_id uuid not null references letter_of_credit(id) on delete cascade,
 condition_name varchar(100) not null,
 condition_value text,
 primary key(lc_id,condition_name)
);
