-- OCR tolerance per tenant: the minimum confidence of a recognised word for it to count as certain (uncertain values are flagged, automatic splitting needs certain pages).
-- NULL = the installation default (lc.ocr.confidence-threshold, 0.80).
ALTER TABLE tenant ADD COLUMN ocr_confidence_threshold numeric(3,2);
ALTER TABLE tenant ADD CONSTRAINT tenant_ocr_confidence_threshold_range CHECK (ocr_confidence_threshold IS NULL OR (ocr_confidence_threshold >= 0.50 AND ocr_confidence_threshold <= 0.95));
