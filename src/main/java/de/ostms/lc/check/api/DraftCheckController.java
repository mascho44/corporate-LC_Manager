package de.ostms.lc.check.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.DraftCheckService;
import de.ostms.lc.document.domain.DocumentType;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** A third-party draft checked against the credit before the original is issued; the draft is not stored. */
@RestController
public class DraftCheckController {
    private final DraftCheckService drafts;
    private final AuditService audit;

    public DraftCheckController(DraftCheckService drafts, AuditService audit) { this.drafts = drafts; this.audit = audit; }

    @PostMapping(value = "/api/lcs/{lcId}/draft-check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftCheckService.Result check(@PathVariable UUID lcId, @RequestPart("file") MultipartFile file, @RequestParam DocumentType type, Authentication authentication) throws IOException {
        var result = drafts.check(lcId, file.getBytes(), file.getOriginalFilename(), file.getContentType(), type);
        audit.record(authentication, "DRAFT_CHECKED", "LETTER_OF_CREDIT", lcId, type.getDisplayName() + " · " + result.discrepancies() + " Abweichungen · " + result.warnings() + " Hinweise (Entwurf nicht gespeichert)");
        return result;
    }
}
