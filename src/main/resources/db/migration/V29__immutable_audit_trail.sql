create function reject_audit_event_mutation() returns trigger as $$
begin
    raise exception 'audit_event is append-only: % is not permitted', tg_op;
end;
$$ language plpgsql;

create trigger trg_audit_event_no_update_delete
    before update or delete on audit_event
    for each row execute function reject_audit_event_mutation();

create trigger trg_audit_event_no_truncate
    before truncate on audit_event
    for each statement execute function reject_audit_event_mutation();
