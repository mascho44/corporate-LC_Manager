package de.ostms.lc.document.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.DocumentSetService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** All documents of a shipment in one step (invoice, packing list, certificates). */
@RestController
@RequestMapping("/api/lcs/{lcId}/document-set")
public class DocumentSetController {
    private final DocumentSetService service;
    private final AuditService audit;

    public DocumentSetController(DocumentSetService service, AuditService audit) { this.service = service; this.audit = audit; }

    /** Defaults for the form: documents the credit names, an invoice number, one goods line from the credit and the originals/signature it demands. */
    @GetMapping("/proposal")
    public DocumentSetService.Proposal proposal(@PathVariable UUID lcId) { return service.proposal(lcId); }

    @PostMapping
    public DocumentSetService.Result create(@PathVariable UUID lcId, @Valid @RequestBody DocumentSetRequest request, Authentication authentication) throws IOException {
        var result = service.create(lcId, request);
        audit.record(authentication, "DOCUMENT_SET_GENERATED", "LETTER_OF_CREDIT", lcId,
            result.documents().size() + " Dokumente erzeugt (" + request.invoiceNumber() + ") · Hinweise " + result.warnings().size() + " · Befunde " + result.findings().size());
        return result;
    }
}
