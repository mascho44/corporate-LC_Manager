package de.corporate.lc.check.api;

import de.corporate.lc.check.service.DocumentCheckService;
import de.corporate.lc.audit.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/lcs/{lcId}/document-checks")
public class DocumentCheckController {
    private final DocumentCheckService service;private final AuditService audit;
    public DocumentCheckController(DocumentCheckService service,AuditService audit) { this.service = service;this.audit=audit; }
    @GetMapping public ReviewSummary check(@PathVariable UUID lcId) { return service.check(lcId); }
    @PostMapping("/decisions") public ReviewSummary decide(@PathVariable UUID lcId,@Valid @RequestBody CheckDecisionRequest request,Authentication authentication){service.decide(lcId,request,authentication.getName());audit.record(authentication,"DOCUMENT_CHECK_DECIDED","LETTER_OF_CREDIT",lcId,request.findingCode()+" · "+request.decision()+" · "+(request.comment()==null?"":request.comment()));return service.check(lcId);}
}
