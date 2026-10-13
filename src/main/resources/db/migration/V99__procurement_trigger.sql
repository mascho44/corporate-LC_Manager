-- Procurement reminders are a fourth automation trigger.
ALTER TABLE automation_rule DROP CONSTRAINT automation_rule_trigger;
ALTER TABLE automation_rule ADD CONSTRAINT automation_rule_trigger CHECK (trigger_type IN ('EBICS_MESSAGE','INBOX_ITEM','DEADLINE','PROCUREMENT'));
