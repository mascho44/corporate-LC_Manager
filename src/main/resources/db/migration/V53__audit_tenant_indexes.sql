-- Keep the existing append-only triggers and historical event attribution intact.
CREATE INDEX audit_event_tenant_occurred_idx ON audit_event(tenant_id, occurred_at DESC);
CREATE INDEX audit_event_tenant_entity_occurred_idx ON audit_event(tenant_id, entity_id, occurred_at DESC);
CREATE INDEX integration_outbox_tenant_status_due_idx ON integration_outbox(tenant_id, status, next_attempt_at, created_at);
