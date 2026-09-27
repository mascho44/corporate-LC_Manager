package de.corporate.lc.check.api;

import de.corporate.lc.check.service.DocumentCheckService;
import de.corporate.lc.check.service.DocumentCheckReportService;
import de.corporate.lc.audit.service.AuditService;
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
    @PostMapping("/decisions") public ReviewSummary decide(@PathVariable UUID lcId,@Valid @RequestBody CheckDecisionRequest request,Authentication authentication){service.decide(lcId,request,authentication.getName());audit.record(authentication,"DOCUMENT_CHECK_DECIDED","LETTER_OF_CREDIT",lcId,request.findingCode()+" · "+request.decision()+" · "+(request.comment()==null?"":request.comment()));return service.check(lcId);}
    @GetMapping(value="/report",produces=MediaType.APPLICATION_PDF_VALUE) public ResponseEntity<byte[]> report(@PathVariable UUID lcId,Authentication authentication)throws IOException{var report=reports.create(lcId);audit.record(authentication,"DOCUMENT_CHECK_REPORT_CREATED","LETTER_OF_CREDIT",lcId,report.filename());return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(report.filename()).build().toString()).contentLength(report.content().length).body(report.content());}
}
