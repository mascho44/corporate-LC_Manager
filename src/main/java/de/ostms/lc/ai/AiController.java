package de.ostms.lc.ai;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.rulepack.DocumentFactSuggester;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
public class AiController {
 private final AiFactSuggester suggester;private final LcDocumentRepository documents;private final AuditService audit;
 public AiController(AiFactSuggester s,LcDocumentRepository d,AuditService a){suggester=s;documents=d;audit=a;}
 public record Status(boolean enabled,String model){}
 @GetMapping("/api/ai/status")
 public Status status(){return new Status(suggester.enabled(),suggester.model());}
 /** Slow (the model runs locally): called on demand from the document facts dialog, never as part of the normal suggestions. */
 @PostMapping("/api/lcs/{lcId}/documents/{id}/rule-facts/ai-suggestions") @Transactional(readOnly=true)
 public List<DocumentFactSuggester.Suggestion> suggest(@PathVariable UUID lcId,@PathVariable UUID id,Authentication auth)throws IOException{
  if(!suggester.enabled())throw new IllegalStateException("KI-Vorschläge sind nicht aktiviert.");
  var doc=documents.findById(id).orElseThrow();
  if(!doc.getLetterOfCredit().getId().equals(lcId))throw new NoSuchElementException("Dokument gehört nicht zu dieser LC-Akte.");
  var result=suggester.suggest(doc);
  audit.record(auth,"AI_SUGGESTIONS_REQUESTED","LC_DOCUMENT",id,"Modell "+suggester.model()+" · "+result.size()+" geprüfte Vorschläge");
  return result;
 }
}
