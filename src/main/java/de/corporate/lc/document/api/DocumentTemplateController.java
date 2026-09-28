package de.corporate.lc.document.api;

import de.corporate.lc.audit.service.AuditService;import de.corporate.lc.document.domain.*;import de.corporate.lc.document.service.DocumentTemplateService;import org.springframework.http.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import java.io.IOException;import java.util.*;

@RestController @RequestMapping("/api/document-templates")
public class DocumentTemplateController {
    private final DocumentTemplateService service;private final AuditService audit;
    public DocumentTemplateController(DocumentTemplateService service,AuditService audit){this.service=service;this.audit=audit;}
    @GetMapping public List<DocumentTemplateView> list(){return service.list();}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public DocumentTemplateView upload(@RequestParam DocumentType type,@RequestPart("file")MultipartFile file,Authentication authentication)throws IOException{var result=service.save(type,file,authentication.getName());audit.record(authentication,"DOCUMENT_TEMPLATE_SAVED","DOCUMENT_TEMPLATE",result.id(),result.documentType()+" · "+result.originalFilename());return result;}
    @GetMapping("/{id}/content") public ResponseEntity<byte[]> download(@PathVariable UUID id){var template=service.one(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(template.getContentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(template.getOriginalFilename()).build().toString()).body(template.getContent());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication authentication){var template=service.delete(id);audit.record(authentication,"DOCUMENT_TEMPLATE_DELETED","DOCUMENT_TEMPLATE",id,template.getDocumentType()+" · "+template.getOriginalFilename());}
}
