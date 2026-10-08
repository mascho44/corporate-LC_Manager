package de.ostms.lc.rulepack;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.DocumentCheckService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

@RestController @RequestMapping("/api/lcs/{lcId}")
public class RuleFactsController {
 private final LetterOfCreditRepository lcs;private final LcDocumentRepository documents;
 private final AuditService audit;private final DocumentCheckService checks;
 public RuleFactsController(LetterOfCreditRepository l,LcDocumentRepository d,AuditService a,DocumentCheckService c){lcs=l;documents=d;audit=a;checks=c;}
 @GetMapping("/rule-facts") @Transactional(readOnly=true)
 public Map<Field,String> lc(@PathVariable UUID lcId){return RuleFacts.read(lcs.findById(lcId).orElseThrow().getRuleFactsJson());}
 @GetMapping("/rule-facts/definitions")
 public Map<String,List<RuleFacts.Definition>> definitions(@PathVariable UUID lcId){
  if(!lcs.existsById(lcId))throw new NoSuchElementException();
  return Map.of("document",RuleFacts.definitions(true),"lc",RuleFacts.definitions(false),"requirements",RuleFacts.requirementDefinitions());
 }
 @GetMapping("/rule-requirements/{type}") @Transactional(readOnly=true)
 public Map<Field,String> requirements(@PathVariable UUID lcId,@PathVariable de.ostms.lc.document.domain.DocumentType type){
  return RuleRequirements.read(lcs.findById(lcId).orElseThrow().getRuleRequirementsJson()).getOrDefault(type,Map.of());
 }
 @PutMapping("/rule-requirements/{type}") @Transactional
 public Map<Field,String> saveRequirements(@PathVariable UUID lcId,@PathVariable de.ostms.lc.document.domain.DocumentType type,jakarta.servlet.http.HttpServletRequest request,Authentication auth)throws java.io.IOException{
  return updateRequirements(lcId,type,RuleFacts.decodeRequest(request.getInputStream().readNBytes(RuleFacts.MAX_BYTES+1)),auth);
 }
 @Transactional
 public Map<Field,String> updateRequirements(UUID lcId,de.ostms.lc.document.domain.DocumentType type,Map<Field,String> facts,Authentication auth){
  var lc=lcs.findById(lcId).orElseThrow();String before=lc.getRuleRequirementsJson();
  String after=RuleRequirements.update(before,type,facts);lc.setRuleRequirementsJson(after);lcs.save(lc);
  long reset=checks.invalidateDecisions(lcId);
  audit.recordInTransaction(auth,"LC_RULE_REQUIREMENTS_UPDATED","LETTER_OF_CREDIT",lcId,"Typ "+type+" · SHA-256 vorher="+RuleFacts.fingerprint(before)+" nachher="+RuleFacts.fingerprint(after)+" · "+reset+" Prüfentscheidungen zurückgesetzt");
  return RuleRequirements.read(after).get(type);
 }
 @PutMapping("/rule-facts") @Transactional
 public Map<Field,String> saveLc(@PathVariable UUID lcId,jakarta.servlet.http.HttpServletRequest request,Authentication auth)throws java.io.IOException{
  return updateLc(lcId,RuleFacts.decodeRequest(request.getInputStream().readNBytes(RuleFacts.MAX_BYTES+1)),auth);
 }
 @Transactional
 public Map<Field,String> updateLc(UUID lcId,Map<Field,String> facts,Authentication auth){
  var lc=lcs.findById(lcId).orElseThrow();var before=lc.getRuleFactsJson();String after=RuleFacts.encode(facts,false);
  lc.setRuleFactsJson(after);lcs.save(lc);long reset=checks.invalidateDecisions(lcId);
  audit.recordInTransaction(auth,"LC_RULE_FACTS_UPDATED","LETTER_OF_CREDIT",lcId,"LC-Prüfdaten SHA-256 vorher="+RuleFacts.fingerprint(before)+" nachher="+RuleFacts.fingerprint(after)+" · Felder "+facts.keySet()+" · "+reset+" Prüfentscheidungen zurückgesetzt");
  return RuleFacts.read(after);
 }
 @GetMapping("/documents/{id}/rule-facts") @Transactional(readOnly=true)
 public Map<Field,String> document(@PathVariable UUID lcId,@PathVariable UUID id){return RuleFacts.read(documentInLc(lcId,id).getRuleFactsJson());}
 @PutMapping("/documents/{id}/rule-facts") @Transactional
 public Map<Field,String> saveDocument(@PathVariable UUID lcId,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request,Authentication auth)throws java.io.IOException{
  return updateDocument(lcId,id,RuleFacts.decodeRequest(request.getInputStream().readNBytes(RuleFacts.MAX_BYTES+1)),auth);
 }
 @Transactional
 public Map<Field,String> updateDocument(UUID lcId,UUID id,Map<Field,String> facts,Authentication auth){
  var doc=documentInLc(lcId,id);var before=doc.getRuleFactsJson();String after=RuleFacts.encode(facts,true);
  doc.setRuleFactsJson(after);documents.save(doc);long reset=checks.invalidateDecisions(lcId);
  audit.recordInTransaction(auth,"DOCUMENT_RULE_FACTS_UPDATED","DOCUMENT",id,"LC "+lcId+" · Dokument-Prüfdaten SHA-256 vorher="+RuleFacts.fingerprint(before)+" nachher="+RuleFacts.fingerprint(after)+" · Felder "+facts.keySet()+" · "+reset+" Prüfentscheidungen zurückgesetzt");
  return RuleFacts.read(after);
 }
 private de.ostms.lc.document.domain.LcDocument documentInLc(UUID lcId,UUID id){
  var doc=documents.findById(id).orElseThrow();
  if(!doc.getLetterOfCredit().getId().equals(lcId))throw new NoSuchElementException("Dokument gehört nicht zu dieser LC-Akte.");
  return doc;
 }
}
