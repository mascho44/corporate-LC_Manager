package de.ostms.lc.monitoring.api;

import de.ostms.lc.monitoring.service.OperationsMonitoringService;
import de.ostms.lc.monitoring.service.OperationsMonitoringSummary;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.monitoring.service.MailStatusService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitoring")
public class OperationsMonitoringController {
    private final OperationsMonitoringService monitoring;
    private final MailStatusService mailStatus;
    private final AuditService audit;

    public OperationsMonitoringController(OperationsMonitoringService monitoring, MailStatusService mailStatus, AuditService audit) { this.monitoring = monitoring; this.mailStatus = mailStatus; this.audit = audit; }

    @GetMapping("/summary")
    public OperationsMonitoringSummary summary() { return monitoring.snapshot(); }

    @GetMapping("/mail")
    public MailStatusService.Status mail() { return mailStatus.status(); }

    /** Test mail to the administrator's own stored address. */
    @PostMapping("/mail/test")
    public java.util.Map<String, String> mailTest(Authentication auth) {
        String address = mailStatus.sendTest(auth.getName());
        audit.record(auth, "MAIL_TEST_SENT", "USER", auth.getName(), "Test-Mail");
        return java.util.Map.of("sentTo", address);
    }
}
