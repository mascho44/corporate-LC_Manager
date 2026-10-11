package de.ostms.lc.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 private static final ObjectMapper JSON=new ObjectMapper();
 private final AiFactSuggester suggester;private final AiSettingsService settings;private final LlmClientFactory factory;private final LcDocumentRepository documents;private final AuditService audit;
 public AiController(AiFactSuggester s,AiSettingsService st,LlmClientFactory factory,LcDocumentRepository d,AuditService a){suggester=s;settings=st;this.factory=factory;documents=d;audit=a;}

 /** What the UI needs: is AI usable for the current tenant, and the tenant's configuration (never the key). */
 @GetMapping("/api/ai/status") public AiSettingsService.View status(){return settings.view();}
 @PutMapping("/api/ai/settings")
 public AiSettingsService.View change(@RequestBody Map<String,Boolean> body,Authentication auth){
  boolean wanted=body!=null&&Boolean.TRUE.equals(body.get("enabled"));
  boolean before=settings.change(wanted,auth.getName());
  audit.record(auth,"AI_SETTING_CHANGED","TENANT",null,"KI-Vorschläge "+(before?"an":"aus")+" → "+(wanted?"an":"aus"));
  return settings.view();
 }
 /** The tenant's own provider configuration. The key is write-only; an empty key keeps the stored one. */
 @PutMapping("/api/ai/config")
 public AiSettingsService.View configure(@RequestBody AiSettingsService.Config body,Authentication auth){
  String summary=settings.configure(body,auth.getName());
  audit.record(auth,"AI_CONFIG_CHANGED","TENANT",null,summary);
  return settings.view();
 }
 @DeleteMapping("/api/ai/config/key")
 public AiSettingsService.View clearKey(Authentication auth){
  settings.clearKey(auth.getName());
  audit.record(auth,"AI_KEY_REMOVED","TENANT",null,"API-Schlüssel entfernt, KI-Vorschläge ausgeschaltet");
  return settings.view();
 }
 /** Sends an invented mini document to the configured model and checks that the answer is usable JSON. No tenant data leaves the system. */
 @PostMapping("/api/ai/test")
 public Map<String,Object> test(Authentication auth){
  long start=System.currentTimeMillis();
  try{
   var resolved=factory.resolveOrThrow().orElseThrow(()->new IllegalStateException("Es ist kein KI-Dienst konfiguriert."));
   String answer=resolved.client().generateJson("Reply with JSON only: {\"vessel\": value, \"quote\": exact quote} for this text. Text: \"Vessel: MSC EXAMPLE, voyage 1\"");
   boolean json;try{JSON.readTree(answer);json=!answer.isBlank();}catch(IOException e){json=false;}
   long ms=System.currentTimeMillis()-start;
   audit.record(auth,"AI_TEST","TENANT",null,resolved.client().provider()+"/"+resolved.client().model()+" · "+(json?"ok":"Antwort nicht verwertbar")+" · "+ms+" ms");
   return Map.of("ok",json,"millis",ms,"provider",resolved.client().provider(),"model",resolved.client().model(),"message",json?"Verbindung funktioniert.":"Der Dienst antwortet, aber nicht mit auswertbarem JSON.");
  }catch(IOException|RuntimeException e){
   audit.record(auth,"AI_TEST","TENANT",null,"fehlgeschlagen: "+e.getMessage());
   return Map.of("ok",false,"millis",System.currentTimeMillis()-start,"message",String.valueOf(e.getMessage()));
  }
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
  var client=factory.current().orElseThrow(()->new IllegalStateException("Es ist kein KI-Dienst konfiguriert."));
  var result=new AiHintService(client).hints(list);
  audit.record(auth,"AI_HINTS_REQUESTED","LETTER_OF_CREDIT",lcId,client.provider()+"/"+client.model()+" · "+result.size()+" Hinweise bei "+list.size()+" Dokumenten");
  return result;
 }
}
