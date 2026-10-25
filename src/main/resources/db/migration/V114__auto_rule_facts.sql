-- Fields of the check data that were filled automatically from the recognised text (JSON array of field names); a person saving the check data confirms them.
ALTER TABLE lc_document ADD COLUMN auto_rule_facts TEXT;
ALTER TABLE letter_of_credit ADD COLUMN auto_rule_facts TEXT;
