package de.ostms.lc.lc.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record LcTimelineEntry(UUID id, String type, String action, String username, String text, LocalDateTime occurredAt) {}
