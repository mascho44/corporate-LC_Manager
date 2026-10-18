-- LC-Angaben, die im Akkreditiv nicht verlangt sind: Regeln, die nur daran scheitern, gelten dann als nicht anwendbar.
ALTER TABLE letter_of_credit ADD COLUMN rule_waived_json text;
