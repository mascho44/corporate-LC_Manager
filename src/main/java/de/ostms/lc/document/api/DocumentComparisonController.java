package de.ostms.lc.document.api;
import de.ostms.lc.document.service.DocumentComparisonService;
import de.ostms.lc.document.domain.DocumentComparison;
import de.ostms.lc.audit.service.AuditService;
import jakarta.validation.Valid;import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/lcs/{lcId}/document-comparisons")
public class DocumentComparisonController {
 private final DocumentComparisonService service;private final AuditService audit;
 public DocumentComparisonController(DocumentComparisonService service,AuditService audit){this.service=service;this.audit=audit;}
 public record Request(@NotNull UUID beforeId,@NotNull UUID afterId){}
 @GetMapping public List<DocumentComparison> history(@PathVariable UUID lcId){return service.history(lcId);}
 @PostMapping public DocumentComparison compare(@PathVariable UUID lcId,@Valid @RequestBody Request request,Authentication auth){var result=service.compare(lcId,request.beforeId(),request.afterId(),auth.getName());audit.record(auth,"DOCUMENT_COMPARED","LETTER_OF_CREDIT",lcId,request.beforeId()+" → "+request.afterId());return result;}
}
