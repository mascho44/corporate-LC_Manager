package de.corporate.lc.document.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.document.domain.DocumentInboxItem;
import de.corporate.lc.document.service.DocumentInboxService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inbox")
public class DocumentInboxController {
    private final DocumentInboxService service;
    private final AuditService audit;
    private final de.corporate.lc.training.service.AdvisingTrainingService advisingTraining;

    public DocumentInboxController(DocumentInboxService service, AuditService audit,de.corporate.lc.training.service.AdvisingTrainingService advisingTraining) {
        this.service = service;
        this.audit = audit;
        this.advisingTraining=advisingTraining;
    }

    @GetMapping
    public List<DocumentInboxItemView> list() {
        return service.openItems();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<DocumentInboxItemView> upload(@RequestPart("file") List<MultipartFile> files,
                                              Authentication authentication) throws IOException {
        List<DocumentInboxItemView> received = service.receive(files, authentication.getName());
        received.forEach(item -> audit.record(authentication, "DOCUMENT_INBOX_UPLOADED", "DOCUMENT_INBOX", item.id(),
                item.originalFilename() + " · " + item.fileSize() + " Bytes · " + item.extractionStatus()));
        return received;
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        DocumentInboxItem item = service.openItem(id);
        MediaType type = switch (item.getContentType()) {
            case "application/pdf" -> MediaType.APPLICATION_PDF;
            case "image/png" -> MediaType.IMAGE_PNG;
            case "image/jpeg" -> MediaType.IMAGE_JPEG;
            case "text/plain" -> MediaType.TEXT_PLAIN;
            case "application/xml" -> MediaType.APPLICATION_XML;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        boolean inline = MediaType.APPLICATION_PDF.equals(type) || MediaType.IMAGE_PNG.equals(type) || MediaType.IMAGE_JPEG.equals(type);
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.builder(inline ? "inline" : "attachment")
                                .filename(item.getOriginalFilename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(item.getContent());
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentInboxItemView retry(@PathVariable UUID id,Authentication authentication){
        var result=service.retryExtraction(id);
        audit.record(authentication,"DOCUMENT_INBOX_EXTRACTION_RETRIED","DOCUMENT_INBOX",id,"Erkennung erneut in Warteschlange aufgenommen");
        return result;
    }

    @GetMapping("/{id}/split-proposal")
    public de.corporate.lc.document.service.PdfDocumentSplitter.Proposal splitProposal(@PathVariable UUID id) throws Exception {
        return service.splitProposal(id);
    }

    public record SplitRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min=2,max=100)
                               List<de.corporate.lc.document.service.PdfDocumentSplitter.Part> parts) {}

    @PostMapping("/{id}/split")
    public List<DocumentInboxItemView> split(@PathVariable UUID id,@Valid @RequestBody SplitRequest request,Authentication authentication) throws Exception {
        var result=service.split(id,request.parts(),authentication.getName());
        audit.record(authentication,"DOCUMENT_INBOX_SPLIT","DOCUMENT_INBOX",id,"Sammel-PDF aufgeteilt · "+result.size()+" Teile · Original erhalten");
        result.forEach(item->audit.record(authentication,"DOCUMENT_INBOX_SPLIT_PART","DOCUMENT_INBOX",item.id(),
            "Original "+id+" · Seiten "+item.sourceFromPage()+"–"+item.sourceToPage()+" · "+item.classification().suggestedType()));
        return result;
    }

    @PostMapping("/{id}/attach")
    public DocumentInboxAttachResult attach(@PathVariable UUID id,
                                             @Valid @RequestBody DocumentInboxAttachRequest request,
                                             Authentication authentication) {
        DocumentInboxAttachResult result = service.attach(id, request);
        String assignmentSource=result.inboxItem().assignmentCandidates().stream().filter(candidate->candidate.lcId().equals(request.lcId()))
                .findFirst().map(candidate->" · LC-Vorschlag bestätigt: "+candidate.reason()).orElse(" · Manuell zugeordnet");
        audit.record(authentication, "DOCUMENT_INBOX_ATTACHED", "LETTER_OF_CREDIT",
                request.lcId(), result.document().originalFilename() + " · " + request.documentType()
                        + " · Posteingang " + id + " · Dokument " + result.document().id() + assignmentSource);
        return result;
    }

    @PostMapping("/{id}/new-case")
    public DocumentInboxService.NewCaseResult createCase(@PathVariable UUID id,@Valid @RequestBody InboxNewCaseRequest request,Authentication authentication){
        if(authentication.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("PERM_LC_EDIT")))throw new org.springframework.security.access.AccessDeniedException("Keine Berechtigung zur Aktenanlage.");
        var result=service.createCase(id,request);
        var lcId=result.lcId();
        audit.record(authentication,"LC_CREATED","LETTER_OF_CREDIT",lcId,"Neue Akte aus Posteingang · "+request.reference());
        audit.record(authentication,"DOCUMENT_INBOX_ATTACHED","LETTER_OF_CREDIT",lcId,result.attachment().document().originalFilename()+" · Posteingang "+id+" · Dokument "+result.attachment().document().id());
        return result;
    }

    @GetMapping("/{id}/advising-preview")
    public de.corporate.lc.document.service.AdvisingLetterExtractor.Proposal advisingPreview(@PathVariable UUID id,@RequestParam(required=false) String bank){
        return advisingTraining.preview(bank,service.openItem(id).getExtractedText());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        DocumentInboxItem item = service.delete(id);
        audit.record(authentication, "DOCUMENT_INBOX_DELETED", "DOCUMENT_INBOX", id,
                item.getOriginalFilename());
    }
}
