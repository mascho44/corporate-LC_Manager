-- MT760 guarantees are kept as dossiers next to letters of credit; MT199/MT799 free-format messages are stored per tenant and linked to a dossier when the reference matches.
ALTER TABLE letter_of_credit ADD COLUMN instrument_type varchar(12) NOT NULL DEFAULT 'LC';
ALTER TABLE letter_of_credit ADD CONSTRAINT letter_of_credit_instrument_type CHECK (instrument_type IN ('LC','GUARANTEE'));

CREATE TABLE swift_message (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 lc_id uuid REFERENCES letter_of_credit(id) ON DELETE CASCADE,
 message_type varchar(10) NOT NULL,
 reference varchar(35) NOT NULL,
 related_reference varchar(35),
 narrative text NOT NULL,
 raw_message text NOT NULL,
 source varchar(20) NOT NULL DEFAULT 'IMPORT',
 imported_by varchar(100),
 imported_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT swift_message_type CHECK (message_type IN ('MT199','MT799'))
);
CREATE INDEX swift_message_lc ON swift_message(tenant_id, lc_id);
CREATE UNIQUE INDEX swift_message_unique ON swift_message(tenant_id, message_type, reference, md5(raw_message));

ALTER TABLE ebics_message DROP CONSTRAINT ebics_message_type;
ALTER TABLE ebics_message ADD CONSTRAINT ebics_message_type CHECK (message_type IN ('MT199','MT700','MT707','MT710','MT760','MT799'));
