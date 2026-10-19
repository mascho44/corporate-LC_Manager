package de.ostms.lc.document.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.SubmissionPackageService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The package for presenting documents to the bank: cover letter plus the documents in order. */
@RestController
@RequestMapping("/api/lcs/{lcId}/submission-package")
public class SubmissionPackageController {
    private final SubmissionPackageService service;
    private final AuditService audit;

    public SubmissionPackageController(SubmissionPackageService service, AuditService audit) { this.service = service; this.audit = audit; }

    /** Documents that can be presented, with originals and copies the credit asks for, the recipient and the open discrepancies of the pre-check. */
    @GetMapping("/plan")
    public SubmissionPackageService.Plan plan(@PathVariable UUID lcId) { return service.plan(lcId); }

    @PostMapping(produces = "application/zip")
    public ResponseEntity<byte[]> build(@PathVariable UUID lcId, @Valid @RequestBody SubmissionPackageRequest request, Authentication authentication) throws IOException {
        var result = service.build(lcId, request);
        audit.record(authentication, "SUBMISSION_PACKAGE_CREATED", "LETTER_OF_CREDIT", lcId, result.filename() + " · " + request.documents().size() + " Dokumente");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.filename()).build().toString())
            .contentLength(result.content().length).body(result.content());
    }
}
