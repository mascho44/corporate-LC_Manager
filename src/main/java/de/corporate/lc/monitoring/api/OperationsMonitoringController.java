package de.corporate.lc.monitoring.api;

import de.corporate.lc.monitoring.service.OperationsMonitoringService;
import de.corporate.lc.monitoring.service.OperationsMonitoringSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitoring")
public class OperationsMonitoringController {
    private final OperationsMonitoringService monitoring;

    public OperationsMonitoringController(OperationsMonitoringService monitoring) { this.monitoring = monitoring; }

    @GetMapping("/summary")
    public OperationsMonitoringSummary summary() { return monitoring.snapshot(); }
}
