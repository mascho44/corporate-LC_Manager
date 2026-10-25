create table training_learning_control (
    rule_id varchar(64) primary key,
    active boolean not null,
    updated_by varchar(100),
    updated_at timestamp not null
);
