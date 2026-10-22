-- MT701, MT734 and MT750 are stored like the free-format messages; their fields are kept by code as JSON next to the readable text.
ALTER TABLE swift_message DROP CONSTRAINT swift_message_type;
ALTER TABLE swift_message ADD CONSTRAINT swift_message_type CHECK (message_type IN ('MT199','MT799','MT701','MT734','MT750'));
ALTER TABLE swift_message ADD COLUMN fields_json text;
ALTER TABLE ebics_message DROP CONSTRAINT ebics_message_type;
ALTER TABLE ebics_message ADD CONSTRAINT ebics_message_type CHECK (message_type IN ('MT199','MT700','MT701','MT707','MT710','MT720','MT734','MT750','MT760','MT799'));
