package de.corporate.lc.document.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.service.DocumentService;
import de.corporate.lc.document.service.GeneratedDocumentService;
import de.corporate.lc.check.service.DocumentCheckService;
import jakarta.validation.Valid;
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
    private final GeneratedDocumentService generated;
    private final DocumentCheckService checks;
    public DocumentController(DocumentService service, AuditService audit,GeneratedDocumentService generated,DocumentCheckService checks) { this.service = service; this.audit = audit;this.generated=generated;this.checks=checks; }

    @PostMapping(value="/lcs/{lcId}/generated-documents",consumes=MediaType.APPLICATION_JSON_VALUE)
    public DocumentView generate(@PathVariable UUID lcId,@Valid @RequestBody GeneratedDocumentRequest request,Authentication authentication)throws IOException{DocumentView result=generated.create(lcId,request);resetChecks(lcId,authentication,"Dokument erstellt");audit.record(authentication,"DOCUMENT_GENERATED","LETTER_OF_CREDIT",lcId,result.originalFilename()+" · "+request.type());return result;}

    @PostMapping(value = "/lcs/{lcId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView upload(@PathVariable UUID lcId, @RequestPart("file") MultipartFile file,
            @RequestParam DocumentType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String currency, Authentication authentication) throws IOException {
        DocumentView document = service.upload(lcId, file, type, documentDate, amount, currency);
        resetChecks(lcId,authentication,"Dokument hochgeladen");
        audit.record(authentication, "DOCUMENT_UPLOADED", "LETTER_OF_CREDIT", lcId, file.getOriginalFilename()+" · "+type);
        return document;
    }
    private void resetChecks(UUID lcId,Authentication authentication,String reason){long reset=checks.invalidateDecisions(lcId);if(reset>0)audit.record(authentication,"DOCUMENT_CHECK_DECISIONS_RESET","LETTER_OF_CREDIT",lcId,reset+" Entscheidungen zurückgesetzt · "+reason);}

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
