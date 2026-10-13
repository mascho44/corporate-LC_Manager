package de.ostms.lc.lc.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.lc.domain.ProcurementItemStatus;
import de.ostms.lc.lc.service.ProcurementPlanService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class LcProcurementController {
    public record LeadTimeRequest(Integer workingDays) {}
    public record StatusRequest(String requirement, String status, String note) {}

    private final ProcurementPlanService plans;
    private final AuditService audit;
    public LcProcurementController(ProcurementPlanService plans, AuditService audit) { this.plans = plans; this.audit = audit; }

    @GetMapping("/api/procurement-overview")
    public List<ProcurementPlanService.Summary> overview() { return plans.summaries(); }

    @GetMapping("/api/lcs/{id}/procurement-plan")
    public ProcurementPlanService.Plan plan(@PathVariable UUID id) { return plans.forLc(id); }

    @PutMapping("/api/lcs/{id}/procurement-status")
    public ProcurementPlanService.Plan setStatus(@PathVariable UUID id, @RequestBody StatusRequest request, Authentication authentication) {
        if (request == null || request.requirement() == null || request.status() == null) throw new IllegalArgumentException("Anforderung und Status sind erforderlich.");
        ProcurementItemStatus.Status status;
        try { status = ProcurementItemStatus.Status.valueOf(request.status()); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unbekannter Status."); }
        plans.setStatus(id, request.requirement(), status, request.note(), authentication.getName());
        audit.record(authentication, "PROCUREMENT_STATUS_CHANGED", "LETTER_OF_CREDIT", id, "Status " + status + ": " + abbreviate(request.requirement()));
        return plans.forLc(id);
    }

    @GetMapping("/api/procurement/lead-times")
    public List<ProcurementPlanService.LeadTime> leadTimes() { return plans.leadTimes(); }

    @PutMapping("/api/procurement/lead-times/{type}")
    public List<ProcurementPlanService.LeadTime> setLeadTime(@PathVariable String type, @RequestBody LeadTimeRequest request, Authentication authentication) {
        if (request == null || request.workingDays() == null) throw new IllegalArgumentException("Die Vorlaufzeit ist erforderlich.");
        DocumentType documentType = parse(type);
        plans.setLeadTime(documentType, request.workingDays(), authentication.getName());
        audit.record(authentication, "PROCUREMENT_LEAD_TIME_CHANGED", "TENANT", null, documentType + ": " + request.workingDays() + " Arbeitstage");
        return plans.leadTimes();
    }

    @DeleteMapping("/api/procurement/lead-times/{type}")
    public List<ProcurementPlanService.LeadTime> resetLeadTime(@PathVariable String type, Authentication authentication) {
        DocumentType documentType = parse(type);
        plans.resetLeadTime(documentType);
        audit.record(authentication, "PROCUREMENT_LEAD_TIME_RESET", "TENANT", null, documentType + ": Standard");
        return plans.leadTimes();
    }

    private static DocumentType parse(String type) {
        try { return DocumentType.valueOf(type); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unbekannte Dokumentart."); }
    }
    private static String abbreviate(String text) { return text.length() > 120 ? text.substring(0, 117) + "…" : text; }
}
