-- Every channel into the document inbox (scan station, e-mail, watched folder) leaves the same trace: channel, reference (message id or file path), sender and subject.
ALTER TABLE inbox_capture_info ADD COLUMN channel varchar(30) NOT NULL DEFAULT 'SCAN_STATION';
ALTER TABLE inbox_capture_info ADD COLUMN origin_reference varchar(255);
ALTER TABLE inbox_capture_info ADD COLUMN sender varchar(255);
ALTER TABLE inbox_capture_info ADD COLUMN subject varchar(255);
