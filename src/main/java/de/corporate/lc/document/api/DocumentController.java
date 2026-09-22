package de.corporate.lc.document.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.service.DocumentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService service;
    private final AuditService audit;
    public DocumentController(DocumentService service, AuditService audit) { this.service = service; this.audit = audit; }

    @PostMapping(value = "/lcs/{lcId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView upload(@PathVariable UUID lcId, @RequestPart("file") MultipartFile file,
            @RequestParam DocumentType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String currency, Authentication authentication) throws IOException {
        DocumentView document = service.upload(lcId, file, type, documentDate, amount, currency);
        audit.record(authentication, "DOCUMENT_UPLOADED", "LETTER_OF_CREDIT", lcId, file.getOriginalFilename()+" · "+type);
        return document;
    }

    @GetMapping("/lcs/{lcId}/documents")
    public List<DocumentView> list(@PathVariable UUID lcId) { return service.forLc(lcId); }

    @GetMapping("/documents/{id}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        LcDocument document = service.one(id);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(document.getContentType()); }
        catch (InvalidMediaTypeException exception) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(document.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getOriginalFilename()).build().toString())
                .body(document.getContent());
    }
}
