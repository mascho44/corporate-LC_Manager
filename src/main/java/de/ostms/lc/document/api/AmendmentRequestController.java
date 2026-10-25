package de.ostms.lc.document.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.AmendmentRequestService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Request to amend a credit, as a letter (PDF) or as MT707 field text for a bank portal. Nothing is stored. */
@RestController
@RequestMapping("/api/lcs/{lcId}/amendment-request")
public class AmendmentRequestController {
    private final AmendmentRequestService service;
    private final AuditService audit;

    public AmendmentRequestController(AmendmentRequestService service, AuditService audit) { this.service = service; this.audit = audit; }

    @GetMapping("/proposal")
    public AmendmentRequestService.Proposal proposal(@PathVariable UUID lcId) { return service.proposal(lcId, LocalDate.now()); }

    @PostMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID lcId, @Valid @RequestBody AmendmentRequestInput input, Authentication authentication) throws IOException {
        byte[] content = service.pdf(lcId, input);
        audit.record(authentication, "AMENDMENT_REQUEST_CREATED", "LETTER_OF_CREDIT", lcId, "Änderungsantrag (PDF) an " + input.recipient().lines().findFirst().orElse(""));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Aenderungsantrag.pdf").contentLength(content.length).body(content);
    }

    @PostMapping("/text")
    public Map<String, String> text(@PathVariable UUID lcId, @Valid @RequestBody AmendmentRequestInput input) { return Map.of("text", service.swiftText(lcId, input)); }
}
