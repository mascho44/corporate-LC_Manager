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

    @PostMapping(value="/lcs/{lcId}/generated-documents/docx",consumes=MediaType.APPLICATION_JSON_VALUE)
    public DocumentView generateDocx(@PathVariable UUID lcId,@Valid @RequestBody GeneratedDocumentRequest request,Authentication authentication)throws IOException{DocumentView result=generated.createDocx(lcId,request);resetChecks(lcId,authentication,"Word-Dokument erstellt");audit.record(authentication,"DOCUMENT_GENERATED","LETTER_OF_CREDIT",lcId,result.originalFilename()+" · DOCX · "+request.type());return result;}

    @PostMapping(value = "/lcs/{lcId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView upload(@PathVariable UUID lcId, @RequestPart("file") MultipartFile file,
            @RequestParam DocumentType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String currency,@RequestParam(required=false) Integer copyNumber, Authentication authentication) throws IOException {
        DocumentView document = service.upload(lcId, file, type, documentDate, amount, currency,copyNumber);
        resetChecks(lcId,authentication,"Dokument hochgeladen");
        audit.record(authentication, "DOCUMENT_UPLOADED", "LETTER_OF_CREDIT", lcId, file.getOriginalFilename()+" · "+type);
        return document;
    }
    @PostMapping(value="/lcs/{lcId}/documents/archive",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<DocumentView> uploadArchive(@PathVariable UUID lcId,@RequestPart("file") MultipartFile file,Authentication authentication)throws IOException{
        List<DocumentView> imported=service.uploadArchive(lcId,file);resetChecks(lcId,authentication,"ZIP-Akte hochgeladen");
        audit.record(authentication,"DOCUMENT_ARCHIVE_UPLOADED","LETTER_OF_CREDIT",lcId,file.getOriginalFilename()+" · "+imported.size()+" Dateien importiert");return imported;
    }
    @PostMapping(value="/lcs/{lcId}/documents/batch",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<DocumentView> uploadBatch(@PathVariable UUID lcId,@RequestPart("file") List<MultipartFile> files,@RequestParam List<DocumentType> types,@RequestParam(required=false) List<Integer> copies,Authentication authentication)throws IOException{
        List<DocumentView> imported=service.uploadBatch(lcId,files,types,copies);resetChecks(lcId,authentication,"Dokumenten-Batch hochgeladen");
        audit.record(authentication,"DOCUMENT_BATCH_UPLOADED","LETTER_OF_CREDIT",lcId,files.size()+" Quelldateien · "+imported.size()+" Dokumente importiert");return imported;
    }
    private void resetChecks(UUID lcId,Authentication authentication,String reason){long reset=checks.invalidateDecisions(lcId);if(reset>0)audit.record(authentication,"DOCUMENT_CHECK_DECISIONS_RESET","LETTER_OF_CREDIT",lcId,reset+" Entscheidungen zurückgesetzt · "+reason);}

    @GetMapping("/lcs/{lcId}/documents")
    public List<DocumentView> list(@PathVariable UUID lcId) { return service.forLc(lcId); }

    @DeleteMapping("/lcs/{lcId}/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID lcId,@PathVariable UUID id,Authentication authentication) {
        LcDocument document=service.delete(lcId,id);
        resetChecks(lcId,authentication,"Dokument gelöscht");
        audit.record(authentication,"DOCUMENT_DELETED","LETTER_OF_CREDIT",lcId,document.getOriginalFilename()+" · "+document.getDocumentType());
    }

    @PutMapping("/lcs/{lcId}/documents/{id}")
    public DocumentView update(@PathVariable UUID lcId,@PathVariable UUID id,@Valid @RequestBody DocumentUpdateRequest request,Authentication authentication){
        String previous=documentAuditState(DocumentView.from(service.one(id)));DocumentView document=service.update(lcId,id,request);resetChecks(lcId,authentication,"Dokumentdaten geändert");
        audit.recordChange(authentication,"DOCUMENT_UPDATED","LETTER_OF_CREDIT",lcId,document.originalFilename()+" · "+document.documentType(),previous,documentAuditState(document));return document;
    }

    private String documentAuditState(DocumentView document){return "Datei="+document.originalFilename()+" | Typ="+document.documentType()+" | Kennzeichnung="+de.corporate.lc.document.domain.DocumentCopy.label(document.copyNumber())+" | Datum="+(document.documentDate()==null?"-":document.documentDate())+" | Betrag="+(document.currency()==null?"-":document.currency())+" "+(document.amount()==null?"-":document.amount());}

    @GetMapping("/documents/{id}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        return content(id, false);
    }

    @GetMapping("/documents/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable UUID id) {
        return content(id, true);
    }

    private ResponseEntity<byte[]> content(UUID id, boolean inline) {
        LcDocument document = service.one(id);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(document.getContentType()); }
        catch (InvalidMediaTypeException exception) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        boolean previewable = mediaType.equals(MediaType.APPLICATION_PDF)
                || mediaType.equals(MediaType.IMAGE_PNG) || mediaType.equals(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(document.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline && previewable ? ContentDisposition.inline() : ContentDisposition.attachment())
                                .filename(document.getOriginalFilename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.getContent());
    }
}
