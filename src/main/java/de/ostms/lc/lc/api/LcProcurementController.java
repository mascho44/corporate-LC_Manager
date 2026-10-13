package de.ostms.lc.lc.api;

import de.ostms.lc.lc.service.ProcurementPlanService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class LcProcurementController {
    private final ProcurementPlanService plans;
    public LcProcurementController(ProcurementPlanService plans) { this.plans = plans; }

    @GetMapping("/api/procurement-overview")
    public java.util.List<ProcurementPlanService.Summary> overview() { return plans.summaries(); }

    @GetMapping("/api/lcs/{id}/procurement-plan")
    public ProcurementPlanService.Plan plan(@PathVariable UUID id) { return plans.forLc(id); }
}
