-- SLA-Pausen: Ereignisse, die die Uhr anhalten (z. B. Rueckfrage an den Kunden) und wieder starten lassen (z. B. Antwort eingegangen).
ALTER TABLE sla_rule ADD COLUMN pause_actions varchar(400) NOT NULL DEFAULT '';
ALTER TABLE sla_rule ADD COLUMN resume_actions varchar(400) NOT NULL DEFAULT '';
