package de.corporate.lc.check.api;

import java.util.List;

public record ReviewSummary(String status, long discrepancies, long warnings, long passed, List<CheckResult> results) { }
