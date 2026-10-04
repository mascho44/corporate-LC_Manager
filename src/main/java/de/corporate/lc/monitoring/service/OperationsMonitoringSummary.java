package de.corporate.lc.monitoring.service;

import java.time.LocalDateTime;

public record OperationsMonitoringSummary(LocalDateTime generatedAt, Metric openExaminations, Metric waitingForCustomer,
                                          Metric discrepancies, Metric upcomingDeadlines, Metric approvalQueue,
                                          Metric ebicsErrors, Metric swiftErrors, Metric failedBackgroundJobs) {
    public record Metric(Long value, boolean available, String description) { }
}
