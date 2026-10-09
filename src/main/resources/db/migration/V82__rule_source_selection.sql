-- Regelquelle: eingebaute Pruefungen, importierte Rule Packs oder beides (Standard BOTH = bisheriges Verhalten).
ALTER TABLE tenant ADD COLUMN rule_source_mode VARCHAR(16) NOT NULL DEFAULT 'BOTH';
-- Optional je Akte: abweichende Regelquelle und Auswahl der anzuwendenden Packs (kommagetrennte Pack-IDs, leer = alle aktiven).
ALTER TABLE letter_of_credit ADD COLUMN rule_source_override VARCHAR(16);
ALTER TABLE letter_of_credit ADD COLUMN rule_pack_ids VARCHAR(600);
