package de.ostms.lc.document.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.DocumentApprovalPolicyService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/settings/approval-thresholds")
public class ApprovalThresholdController {
    private final DocumentApprovalPolicyService policy;
    private final AuditService audit;

    public ApprovalThresholdController(DocumentApprovalPolicyService policy, AuditService audit) {
        this.policy = policy;
        this.audit = audit;
    }

    @GetMapping
    public List<ApprovalThresholdView> all() { return policy.all(); }

    @PutMapping
    public List<ApprovalThresholdView> replace(@Valid @RequestBody List<@Valid ApprovalThresholdRequest> rules, Authentication auth) {
        var before = policy.all();
        var after = policy.replace(rules);
        audit.recordChange(auth, "DOCUMENT_APPROVAL_POLICY_UPDATED", "DOCUMENT_APPROVAL_POLICY", "global",
                after.size() + " Betragsstufen", before.toString(), after.toString());
        return after;
    }
}
