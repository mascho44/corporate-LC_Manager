package de.corporate.lc.document.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.document.domain.DocumentDraftStatus;
import de.corporate.lc.document.service.DocumentDraftService;
import de.corporate.lc.check.service.DocumentCheckService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController @RequestMapping("/api/lcs/{lcId}/document-drafts")
public class DocumentDraftController {
    private final DocumentDraftService drafts;private final AuditService audit;private final DocumentCheckService checks;
    public DocumentDraftController(DocumentDraftService drafts,AuditService audit,DocumentCheckService checks){this.drafts=drafts;this.audit=audit;this.checks=checks;}
    @GetMapping public List<DocumentDraftView> list(@PathVariable UUID lcId){return drafts.list(lcId);}
    @PostMapping public DocumentDraftView create(@PathVariable UUID lcId,@Valid @RequestBody GeneratedDocumentRequest request,Authentication auth){var result=drafts.create(lcId,request,auth.getName());audit.record(auth,"DOCUMENT_DRAFT_CREATED","LETTER_OF_CREDIT",lcId,result.documentNumber());return result;}
    @PutMapping("/{id}") public DocumentDraftView update(@PathVariable UUID lcId,@PathVariable UUID id,@Valid @RequestBody GeneratedDocumentRequest request,Authentication auth){var result=drafts.update(lcId,id,request,auth.getName());audit.record(auth,"DOCUMENT_DRAFT_UPDATED","LETTER_OF_CREDIT",lcId,result.documentNumber()+" · Version "+result.version());return result;}
    @PutMapping("/{id}/status") public DocumentDraftView status(@PathVariable UUID lcId,@PathVariable UUID id,@RequestParam DocumentDraftStatus status,Authentication auth){String permission=switch(status){case SUBMITTED,DRAFT->"PERM_DOCUMENT_GENERATE";case REVIEWED->"PERM_DOCUMENT_REVIEW";case FINAL->"PERM_DOCUMENT_APPROVE";};if(auth.getAuthorities().stream().noneMatch(authority->authority.getAuthority().equals(permission)))throw new org.springframework.security.access.AccessDeniedException("Für diesen Schritt fehlt die erforderliche Berechtigung.");var result=drafts.status(lcId,id,status,auth.getName());audit.record(auth,"DOCUMENT_DRAFT_STATUS_CHANGED","LETTER_OF_CREDIT",lcId,result.documentNumber()+" · "+status);return result;}
    @PostMapping("/{id}/generate") public DocumentView generate(@PathVariable UUID lcId,@PathVariable UUID id,@RequestParam(defaultValue="PDF") String format,Authentication auth)throws IOException{var result=drafts.generate(lcId,id,format);long reset=checks.invalidateDecisions(lcId);audit.record(auth,"DOCUMENT_DRAFT_GENERATED","LETTER_OF_CREDIT",lcId,result.originalFilename());if(reset>0)audit.record(auth,"DOCUMENT_CHECK_DECISIONS_RESET","LETTER_OF_CREDIT",lcId,reset+" Entscheidungen wegen neuem finalen Dokument zurückgesetzt");return result;}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID lcId,@PathVariable UUID id,Authentication auth){drafts.delete(lcId,id);audit.record(auth,"DOCUMENT_DRAFT_DELETED","LETTER_OF_CREDIT",lcId,id.toString());}
}
