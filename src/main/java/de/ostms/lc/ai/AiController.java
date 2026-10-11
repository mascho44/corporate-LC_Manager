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
 private final AiFactSuggester suggester;private final AiSettingsService settings;private final LcDocumentRepository documents;private final AuditService audit;
 private final org.springframework.beans.factory.ObjectProvider<LlmClient> client;
 public AiController(AiFactSuggester s,AiSettingsService st,LcDocumentRepository d,AuditService a,org.springframework.beans.factory.ObjectProvider<LlmClient> client){suggester=s;settings=st;documents=d;audit=a;this.client=client;}

 /** What the UI needs: is AI usable for the current tenant, and which model is behind it. */
 @GetMapping("/api/ai/status") public AiSettingsService.View status(){return settings.view();}
 @PutMapping("/api/ai/settings")
 public AiSettingsService.View change(@RequestBody Map<String,Boolean> body,Authentication auth){
  boolean wanted=body!=null&&Boolean.TRUE.equals(body.get("enabled"));
  boolean before=settings.change(wanted,auth.getName());
  audit.record(auth,"AI_SETTING_CHANGED","TENANT",null,"KI-Vorschläge "+(before?"an":"aus")+" → "+(wanted?"an":"aus")+(settings.available()?" · Anbieter "+suggester.provider()+", Modell "+suggester.model():""));
  return settings.view();
 }
 private void requireActive(){if(!settings.active())throw new IllegalStateException("KI-Vorschläge sind für diesen Mandanten nicht aktiviert.");}

 /** Slow: called on demand from the document facts dialog, never as part of the normal suggestions. */
 @PostMapping("/api/lcs/{lcId}/documents/{id}/rule-facts/ai-suggestions") @Transactional(readOnly=true)
 public List<DocumentFactSuggester.Suggestion> suggest(@PathVariable UUID lcId,@PathVariable UUID id,Authentication auth)throws IOException{
  requireActive();
  var doc=documents.findById(id).orElseThrow();
  if(!doc.getLetterOfCredit().getId().equals(lcId))throw new NoSuchElementException("Dokument gehört nicht zu dieser LC-Akte.");
  var result=suggester.suggest(doc);
  audit.record(auth,"AI_SUGGESTIONS_REQUESTED","LC_DOCUMENT",id,suggester.provider()+"/"+suggester.model()+" · "+result.size()+" geprüfte Vorschläge");
  return result;
 }
 /** Non-binding hints across all documents of the file. */
 @PostMapping("/api/lcs/{lcId}/ai-hints") @Transactional(readOnly=true)
 public List<AiHintService.Hint> hints(@PathVariable UUID lcId,Authentication auth)throws IOException{
  requireActive();
  var list=documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
  var result=new AiHintService(client.getObject()).hints(list);
  audit.record(auth,"AI_HINTS_REQUESTED","LETTER_OF_CREDIT",lcId,suggester.provider()+"/"+suggester.model()+" · "+result.size()+" Hinweise bei "+list.size()+" Dokumenten");
  return result;
 }
}
