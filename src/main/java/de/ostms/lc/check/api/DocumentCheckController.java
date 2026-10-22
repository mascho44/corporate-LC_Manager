package de.ostms.lc.check.api;

import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.check.service.DocumentCheckReportService;
import de.ostms.lc.audit.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.io.IOException;import org.springframework.http.*;

@RestController
@RequestMapping("/api/lcs/{lcId}/document-checks")
public class DocumentCheckController {
    private final DocumentCheckService service;private final AuditService audit;private final DocumentCheckReportService reports;
    public DocumentCheckController(DocumentCheckService service,AuditService audit,DocumentCheckReportService reports) { this.service = service;this.audit=audit;this.reports=reports; }
    @GetMapping public ReviewSummary check(@PathVariable UUID lcId) { return service.check(lcId); }
    @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.lc.repository.LetterOfCreditRepository letters;
    /** Draft notice of the confirmed discrepancies (MT734 and letter); nothing is sent. */
    @GetMapping("/discrepancy-notice") public de.ostms.lc.check.service.DiscrepancyNotice.Draft discrepancyNotice(@PathVariable UUID lcId,Authentication authentication){
        var lc=letters.findById(lcId).orElseThrow();
        var draft=de.ostms.lc.check.service.DiscrepancyNotice.create(lc,service.check(lcId).results(),java.time.LocalDate.now());
        if(draft.discrepancies()>0)audit.record(authentication,"DISCREPANCY_NOTICE_CREATED","LETTER_OF_CREDIT",lcId,draft.discrepancies()+" Abweichungen");
        return draft;
    }
    public record PreliminaryReview(String mode,boolean finalReview,java.time.Instant checkedAt,ReviewSummary summary){}
    @GetMapping("/precheck") public PreliminaryReview precheck(@PathVariable UUID lcId){return new PreliminaryReview("PRECHECK",false,java.time.Instant.now(),service.precheck(lcId));}
    @GetMapping("/decisions/history") public java.util.List<de.ostms.lc.check.domain.DocumentCheckDecision> decisionHistory(@PathVariable UUID lcId){return service.decisionHistory(lcId);}
    @PostMapping("/decisions") @org.springframework.transaction.annotation.Transactional public ReviewSummary decide(@PathVariable UUID lcId,@Valid @RequestBody CheckDecisionRequest request,Authentication authentication){var saved=service.decide(lcId,request,authentication.getName(),authentication.getAuthorities().stream().anyMatch(g->"PERM_SANCTIONS_REVIEW".equals(g.getAuthority())));audit.recordInTransaction(authentication,"DOCUMENT_CHECK_DECIDED","LETTER_OF_CREDIT",lcId,request.findingCode()+" · "+request.decision()+" · Katalog "+saved.getRuleCatalogVersion()+" · Regel "+saved.getRuleId()+" v"+saved.getRuleVersion()+" · Befund "+saved.getFindingFingerprint()+" · "+(request.comment()==null?"":request.comment()));if("CONFIRMED_DISCREPANCY".equals(request.decision()))audit.recordInTransaction(authentication,"DOCUMENT_DISCREPANCY_CONFIRMED","LETTER_OF_CREDIT",lcId,request.findingCode());return service.check(lcId);}
    @PutMapping("/requirements")public ReviewSummary mapRequirement(@PathVariable UUID lcId,@Valid @RequestBody RequirementMappingRequest request,Authentication authentication){service.mapRequirement(lcId,request.requirement(),request.documentType(),authentication.getName());audit.record(authentication,"DOCUMENT_REQUIREMENT_MAPPED","LETTER_OF_CREDIT",lcId,request.documentType()+" · "+request.requirement());return service.check(lcId);}
    @GetMapping("/requirements/mappings")public java.util.List<de.ostms.lc.check.domain.LcRequirementMapping> requirementMappings(@PathVariable UUID lcId){return service.requirementMappings(lcId);}
    @DeleteMapping("/requirements")public ReviewSummary removeRequirementMapping(@PathVariable UUID lcId,@RequestParam String requirement,Authentication authentication){service.removeRequirementMapping(lcId,requirement);audit.record(authentication,"DOCUMENT_REQUIREMENT_MAPPING_REMOVED","LETTER_OF_CREDIT",lcId,requirement);return service.check(lcId);}
    @GetMapping(value="/report",produces=MediaType.APPLICATION_PDF_VALUE) public ResponseEntity<byte[]> report(@PathVariable UUID lcId,Authentication authentication)throws IOException{var report=reports.create(lcId);audit.record(authentication,"DOCUMENT_CHECK_REPORT_CREATED","LETTER_OF_CREDIT",lcId,report.filename());return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(report.filename()).build().toString()).contentLength(report.content().length).body(report.content());}
}
