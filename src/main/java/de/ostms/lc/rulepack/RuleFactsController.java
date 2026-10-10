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
 @org.springframework.beans.factory.annotation.Autowired private InternalPackService packs;
 @org.springframework.beans.factory.annotation.Autowired(required=false) private RuleSourceService ruleSource;
 @GetMapping("/documents/{id}/rule-facts/missing") @Transactional(readOnly=true)
 public Set<Field> missing(@PathVariable UUID lcId,@PathVariable UUID id){
  var lc=lcs.findById(lcId).orElseThrow();
  var source=ruleSource==null?RuleSourceService.Effective.defaults():ruleSource.effective(lc);
  if(source.mode()==RuleSourceMode.EMBEDDED)return java.util.Set.of();
  return packs.missingDocumentFields(lc,documentInLc(lcId,id),source.packIds());
 }
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
 @org.springframework.beans.factory.annotation.Autowired(required=false) private DocumentFactSuggester suggester;
 @GetMapping("/documents/{id}/rule-facts/suggestions") @Transactional(readOnly=true)
 public List<DocumentFactSuggester.Suggestion> suggestions(@PathVariable UUID lcId,@PathVariable UUID id){
  return suggester==null?List.of():suggester.suggest(documentInLc(lcId,id));
 }
 @org.springframework.beans.factory.annotation.Autowired(required=false) private LcFactSuggester lcSuggester;
 public record DocumentSuggestions(UUID documentId,String filename,List<DocumentFactSuggester.Suggestion> suggestions){}
 public record SuggestionOverview(List<DocumentFactSuggester.Suggestion> lc,List<DocumentSuggestions> documents,int total){}
 public record Applied(int lcFields,int documentFields,int documents){}
 /** Everything that could be proposed for this dossier: LC-level facts from the parties and document facts from the recognised text. */
 @GetMapping("/rule-facts/suggestions") @Transactional(readOnly=true)
 public SuggestionOverview allSuggestions(@PathVariable UUID lcId){
  var lc=lcs.findById(lcId).orElseThrow();
  var lcSuggestions=lcSuggester==null?List.<DocumentFactSuggester.Suggestion>of():lcSuggester.suggest(lc);
  var perDocument=new ArrayList<DocumentSuggestions>();int total=lcSuggestions.size();
  if(suggester!=null)for(var doc:documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId)){
   var found=suggester.suggest(doc);if(found.isEmpty())continue;
   perDocument.add(new DocumentSuggestions(doc.getId(),doc.getOriginalFilename(),found));total+=found.size();
  }
  return new SuggestionOverview(lcSuggestions,List.copyOf(perDocument),total);
 }
 /** Stores the proposals for fields that are still empty; values that were entered or changed by a person are never touched. */
 @PostMapping("/rule-facts/suggestions/apply") @Transactional
 public Applied applySuggestions(@PathVariable UUID lcId,Authentication auth){
  var overview=allSuggestions(lcId);int lcFields=0,docFields=0,docs=0;
  boolean mayEditLc=auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("PERM_LC_EDIT"));
  var lc=lcs.findById(lcId).orElseThrow();
  if(mayEditLc){
   var merged=new EnumMap<Field,String>(Field.class);merged.putAll(RuleFacts.read(lc.getRuleFactsJson()));int before=merged.size();
   for(var s:overview.lc())if(blank(merged.get(s.field())))merged.put(s.field(),s.value());
   lcFields=merged.size()-before;
   if(lcFields>0)updateLc(lcId,merged,auth);
  }
  for(var entry:overview.documents()){
   var doc=documentInLc(lcId,entry.documentId());
   var merged=new EnumMap<Field,String>(Field.class);merged.putAll(RuleFacts.read(doc.getRuleFactsJson()));int before=merged.size();
   for(var s:entry.suggestions())if(blank(merged.get(s.field())))merged.put(s.field(),s.value());
   int added=merged.size()-before;
   if(added>0){updateDocument(lcId,doc.getId(),merged,auth);docFields+=added;docs++;}
  }
  audit.recordInTransaction(auth,"RULE_FACTS_SUGGESTIONS_APPLIED","LETTER_OF_CREDIT",lcId,lcFields+" LC-Angaben, "+docFields+" Dokumentangaben in "+docs+" Dokumenten übernommen");
  return new Applied(lcFields,docFields,docs);
 }
 private static boolean blank(String v){return v==null||v.isBlank();}
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
