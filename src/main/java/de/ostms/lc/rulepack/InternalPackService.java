package de.ostms.lc.rulepack;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.api.*;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.*;

@Service
public class InternalPackService {
 private final PackCodec codec;private final PackVersionRepository versions;
 private final PackSelectionRepository selections;private final AuditService audit;
 public InternalPackService(PackCodec c,PackVersionRepository v,PackSelectionRepository s,AuditService a){codec=c;versions=v;selections=s;audit=a;}
 public record Preview(PackDefinition definition,String checksum,List<PackEvaluator.TestResult> tests,boolean testsPassed){}
 public Object previewOrInspect(byte[] source){var report=codec.inspectSpecification(source);return report==null?preview(source):report;}
 public Preview preview(byte[] source){
  var pack=codec.parse(source);var tests=PackEvaluator.test(pack);
  return new Preview(pack,codec.digest(codec.canonical(pack)),tests,tests.stream().allMatch(PackEvaluator.TestResult::passed));
 }
 @Transactional
 public StoredPackVersion importPack(byte[] source,Authentication auth){
  var preview=preview(source);var pack=preview.definition();
  if(versions.existsByPackIdAndVersion(pack.packId(),pack.version()))throw new IllegalArgumentException("Diese Pack-Version besteht bereits und kann nicht überschrieben werden.");
  var row=new StoredPackVersion();row.packId=pack.packId();row.version=pack.version();
  row.definitionJson=codec.canonical(pack);row.checksum=preview.checksum();row.testsPassed=preview.testsPassed();row.importedBy=auth.getName();
  versions.saveAndFlush(row);
  if(!selections.existsById(row.packId)){var selection=new PackSelection();selection.id=row.packId;selections.saveAndFlush(selection);}
  audit.recordInTransaction(auth,"LC_RULE_PACK_IMPORTED","RULE_PACK_VERSION",row.id,row.packId+" v"+row.version+" · SHA-256 "+row.checksum+" · Tests "+row.testsPassed);
  return row;
 }
 public record View(UUID id,String packId,String version,String name,String checksum,boolean testsPassed,
                    String importedBy,java.time.LocalDateTime importedAt,boolean active,boolean previous){}
 @Transactional(readOnly=true)
 public List<View> list(){
  var selected=new HashMap<String,PackSelection>();selections.findAll().forEach(p->selected.put(p.id,p));
  return versions.findAllByOrderByImportedAtDesc().stream().map(v->{
   var p=selected.get(v.packId);var definition=read(v);
   return new View(v.id,v.packId,v.version,definition.name(),v.checksum,v.testsPassed,v.importedBy,v.importedAt,
    p!=null&&v.id.equals(p.activeVersionId),p!=null&&v.id.equals(p.previousVersionId));
  }).toList();
 }
 @Transactional(readOnly=true)
 public Preview test(UUID id){
  var v=versions.findById(id).orElseThrow();read(v);
  return preview(v.definitionJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 @Transactional
 public void activate(UUID id,boolean rightsConfirmed,Authentication auth){
  if(!rightsConfirmed)throw new IllegalArgumentException("Nutzungsrechte und interne Freigabe müssen ausdrücklich bestätigt werden.");
  var version=versions.findById(id).orElseThrow();read(version);
  if(!version.testsPassed||!test(id).testsPassed())throw new IllegalArgumentException("Alle Pack-Tests müssen vor Aktivierung bestehen.");
  var selected=selections.locked(version.packId).orElseThrow();
  if(id.equals(selected.activeVersionId))return;
  selected.previousVersionId=selected.activeVersionId;selected.activeVersionId=id;
  audit.recordInTransaction(auth,"LC_RULE_PACK_ACTIVATED","RULE_PACK_VERSION",id,
   version.packId+" v"+version.version+" · SHA-256 "+version.checksum+" · Herkunft "+read(version).origin()+" · ausreichende Lizenz/Nutzungserlaubnis für alle Benutzer und interne Freigabe bestätigt · vorher "+selected.previousVersionId);
 }
 @Transactional
 public void deactivate(String packId,Authentication auth){
  var selected=selections.locked(packId).orElseThrow();
  if(selected.activeVersionId==null)return;
  var old=selected.activeVersionId;selected.previousVersionId=old;selected.activeVersionId=null;
  audit.recordInTransaction(auth,"LC_RULE_PACK_DEACTIVATED","RULE_PACK",packId,"Deaktivierte Version "+old);
 }
 @Transactional
 public void delete(UUID id,Authentication auth){
  var version=versions.findById(id).orElseThrow();
  de.ostms.lc.tenant.domain.TenantContext.require(version.getTenantId());
  var selected=selections.locked(version.packId).orElseThrow();
  if(id.equals(selected.activeVersionId))throw new IllegalArgumentException("Aktives Rule Pack zuerst deaktivieren.");
  if(id.equals(selected.previousVersionId)){selected.previousVersionId=null;selections.flush();}
  audit.recordInTransaction(auth,"LC_RULE_PACK_DELETED","RULE_PACK_VERSION",id,version.packId+" v"+version.version+" · SHA-256 "+version.checksum);
  versions.delete(version);
  versions.flush();
 }
 private PackDefinition read(StoredPackVersion v){
  de.ostms.lc.tenant.domain.TenantContext.require(v.getTenantId());
  if(!codec.digest(v.definitionJson).equals(v.checksum))throw new IllegalStateException("Prüfsumme des Rule Packs stimmt nicht.");
  var pack=codec.parse(v.definitionJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  if(!pack.packId().equals(v.packId)||!pack.version().equals(v.version))throw new IllegalStateException("Pack-Identität stimmt nicht.");
  return pack;
 }
 /** Befunde plus die IDs der AUTOMATISCHEN Regeln der ausgewerteten Packs (für die Regelquelle "Importiert"). */
 public record PackEvaluation(List<CheckResult> findings,Set<String> automaticRuleIds){}
 @Transactional(readOnly=true)
 public List<CheckResult> evaluate(LetterOfCredit lc,List<LcDocument> documents){
  return evaluateSelected(lc,documents,Set.of()).findings();
 }
 /** Wertet die aktiven Packs aus; eine nicht leere Auswahl beschränkt auf diese Pack-IDs. Regeln, die nicht angewendet werden (Dokumenttyp nicht vorgelegt oder Bedingung nicht erfüllt), ergeben je Pack nur eine Zusammenfassung ohne Warnung. */
 @Transactional(readOnly=true)
 public PackEvaluation evaluateSelected(LetterOfCredit lc,List<LcDocument> documents,Set<String> selectedPackIds){
  de.ostms.lc.tenant.domain.TenantContext.require(lc.getTenantId());
  documents.forEach(d->de.ostms.lc.tenant.domain.TenantContext.require(d.getTenantId()));
  var findings=new ArrayList<CheckResult>();
  var automaticRuleIds=new HashSet<String>();
  for(var selection:selections.findAll().stream().sorted(Comparator.comparing(s->s.id)).toList()){
   if(selection.activeVersionId==null)continue;
   if(!selectedPackIds.isEmpty()&&!selectedPackIds.contains(selection.id))continue;
   var stored=versions.findById(selection.activeVersionId).orElseThrow();
   PackDefinition definition;
   try{definition=read(stored);if(!stored.testsPassed)throw new IllegalStateException("Pack wurde nicht erfolgreich getestet.");}
   catch(RuntimeException invalid){
    findings.add(new CheckResult(CheckResult.Severity.WARNING,"INTERNAL_PACK_INVALID",
     "Aktives internes Rule Pack konnte nicht ausgewertet werden. Administration informieren.",
     "Pack "+selection.id,null,"Version "+selection.activeVersionId));continue;
   }
   var notApplied=new TreeMap<String,Integer>();
   var notAppliedByCondition=new ArrayList<String>();
   for(var rule:definition.rules()){
    if(rule.effectiveMode()==PackDefinition.Mode.AUTOMATIC)automaticRuleIds.add(rule.id());
    String code="PACK."+definition.packId()+"."+rule.id();
    var metadata=new RuleDefinition(code,definition.version()+"/"+rule.version(),definition.name()+" · "+rule.id(),
     "Internes Pack "+definition.packId()+" v"+definition.version()+" · "+rule.sourceReference(),
     rule.message(),"Deklarativer Metadatenvergleich. Keine vollständige Dokumentenprüfung oder Rechtsfreigabe.",null);
    var matching=documents.stream().filter(d->d.getDocumentType()==rule.documentType()).toList();
    if(matching.isEmpty()){
     notApplied.merge(rule.documentType().getDisplayName(),1,Integer::sum);
     continue;
    }
    for(var document:matching){
     var facts=new EnumMap<Field,String>(Field.class);
     try{
      if(definition.schemaVersion()==1){facts.put(rule.left(),value(rule.left(),lc,document));facts.put(rule.right(),value(rule.right(),lc,document));}
      else for(var field:Field.values())if(!field.peer()&&(definition.schemaVersion()>=4||(definition.schemaVersion()==3?field.ordinal()<=Field.LC_EXAMINATION_DECISION_DATE.ordinal():field.ordinal()<=15)))facts.put(field,value(field,lc,document));
      if(definition.schemaVersion()>=4)facts.put(Field.LC_INSURANCE_BASE_AMOUNT,ExtendedRuleFacts.insuranceBasis(facts));
      if(rule.parameters()!=null&&rule.parameters().peerDocumentType()!=null){
       var peer=uniquePeer(rule,document,documents);
       for(var field:Field.values())if(field.peer())facts.put(field,peer==null?null:value(peerSource(field),lc,peer));
      }
     }
     catch(RuntimeException invalidFacts){facts.clear();}
     String left=facts.get(rule.left()),right=PackEvaluator.right(rule,facts);
     var outcome=PackEvaluator.evaluate(rule,facts,definition.calendars()==null?List.of():definition.calendars(),definition.schemaVersion()>=3);
     if(outcome==Outcome.NOT_APPLICABLE){notAppliedByCondition.add(rule.id());continue;}
     var level=outcome==Outcome.PASS?CheckResult.Severity.OK:outcome!=Outcome.FAIL||rule.severity()==Level.WARNING?CheckResult.Severity.WARNING:CheckResult.Severity.DISCREPANCY;
     String outcomeLabel=switch(outcome){case PASS->"Regel erfüllt: ";case FAIL->"Regel verletzt: ";case NOT_APPLICABLE->"Regel nicht anwendbar: ";case MANUAL_REVIEW->"Manuelle fachliche Prüfung erforderlich: ";case NOT_EVALUABLE->"Regel nicht prüfbar: ";};
     if(definition.schemaVersion()==1)outcomeLabel=outcome==Outcome.PASS?"Interne Regel erfüllt: ":outcome==Outcome.FAIL?"Interne Regel verletzt: ":"Interne Regel nicht prüfbar: ";
     findings.add(new CheckResult(level,code,outcomeLabel+rule.message(),
      metadata.basis()+" · "+rule.right()+" = "+Objects.toString(right,"nicht erfasst"),
      document.getOriginalFilename(),rule.left()+" = "+Objects.toString(left,"nicht erfasst")+" · "+rule.operator()+" · SHA-256 "+stored.checksum+(definition.schemaVersion()>=2?" · Ergebnis "+outcome+" · Prüfdaten "+facts:"")+peerEvidence(rule,document,documents)).withRule(metadata));
    }
   }
   if(!notApplied.isEmpty()||!notAppliedByCondition.isEmpty()){
    int count=notApplied.values().stream().mapToInt(Integer::intValue).sum()+notAppliedByCondition.size();
    var evidence=new StringBuilder();
    if(!notApplied.isEmpty())evidence.append("Dokumenttyp nicht vorgelegt: ").append(String.join(", ",notApplied.entrySet().stream().map(e->e.getKey()+" ("+e.getValue()+")").toList()));
    if(!notAppliedByCondition.isEmpty()){if(evidence.length()>0)evidence.append("; ");evidence.append("Bedingung nicht erfüllt: ").append(String.join(", ",notAppliedByCondition));}
    String summaryCode="PACK."+definition.packId()+".NOT_APPLIED";
    var summaryRule=new RuleDefinition(summaryCode,definition.version(),definition.name()+" · nicht angewendete Regeln",
     "Internes Pack "+definition.packId()+" v"+definition.version(),"Zusammenfassung der Regeln, die für diese Akte nicht angewendet wurden.","Keine Prüfaussage zu diesen Regeln.",null);
    findings.add(new CheckResult(CheckResult.Severity.OK,summaryCode,count+" von "+definition.rules().size()+" Regeln nicht angewendet (Dokumenttyp nicht vorgelegt oder Bedingung nicht erfüllt).",
     summaryRule.basis(),null,evidence.toString()).withRule(summaryRule));
   }
  }
  return new PackEvaluation(List.copyOf(findings),Set.copyOf(automaticRuleIds));
 }
 @Transactional(readOnly=true)
 public Set<Field> missingDocumentFields(LetterOfCredit lc,LcDocument doc){
  return missingDocumentFields(lc,doc,Set.of());
 }
 @Transactional(readOnly=true)
 public Set<Field> missingDocumentFields(LetterOfCredit lc,LcDocument doc,Set<String> selectedPackIds){
  de.ostms.lc.tenant.domain.TenantContext.require(lc.getTenantId());
  de.ostms.lc.tenant.domain.TenantContext.require(doc.getTenantId());
  var fields=EnumSet.noneOf(Field.class);
  for(var selection:selections.findAll()){
   if(selection.activeVersionId==null)continue;
   if(!selectedPackIds.isEmpty()&&!selectedPackIds.contains(selection.id))continue;
   var stored=versions.findById(selection.activeVersionId).orElseThrow();
   if(!stored.testsPassed)throw new IllegalStateException("Aktives Pack ist nicht erfolgreich getestet.");
   for(var rule:read(stored).rules()){
    if(rule.documentType()!=doc.getDocumentType())continue;
    if(rule.left()!=null)fields.add(rule.left());if(rule.right()!=null)fields.add(rule.right());
    if(rule.conditions()!=null)rule.conditions().forEach(c->fields.add(c.field()));
    if(rule.parameters()!=null){if(rule.parameters().daysField()!=null)fields.add(rule.parameters().daysField());if(rule.parameters().percentField()!=null)fields.add(rule.parameters().percentField());}
   }
  }
  fields.removeIf(field->!RuleFacts.DOCUMENT.contains(field)||field.peer()||value(field,lc,doc)!=null&&!value(field,lc,doc).isBlank());
  return fields;
 }
 private String value(Field field,LetterOfCredit lc,LcDocument doc){
  Object value=switch(field){
   case DOCUMENT_AMOUNT->doc.getAmount();case DOCUMENT_CURRENCY->doc.getCurrency();case DOCUMENT_DATE->doc.getDocumentDate();
   case DOCUMENT_NUMBER->doc.getExtractedDocumentNumber();
   case LITERAL->null;
   case LC_AMOUNT->lc.getAmount();case LC_CURRENCY->lc.getCurrency();case LC_EXPIRY_DATE->lc.getExpiryDate();case LC_LATEST_SHIPMENT_DATE->lc.getLatestShipmentDate();
   case LC_BENEFICIARY->lc.getBeneficiary();case LC_APPLICANT->lc.getApplicant();
   case LC_DOCUMENT_ISSUED_ORIGINAL_COUNT->RuleFacts.read(doc.getRuleFactsJson()).get(Field.DOCUMENT_ISSUED_ORIGINAL_COUNT);
   case LC_SIGNATURE_REQUIRED,LC_REQUIRED_ORIGINAL_COUNT,LC_REQUIRED_ISSUER->RuleRequirements.read(lc.getRuleRequirementsJson()).getOrDefault(doc.getDocumentType(),Map.of()).get(field);
   default->field.peer()?null:RuleFacts.read(field.document()?doc.getRuleFactsJson():lc.getRuleFactsJson()).get(field);
  };
  return value==null?null:value instanceof java.math.BigDecimal number?number.toPlainString():value.toString();
 }
 private LcDocument uniquePeer(Rule rule,LcDocument document,List<LcDocument> documents){
  String group=RuleFacts.read(document.getRuleFactsJson()).get(Field.DOCUMENT_PRESENTATION_GROUP);
  var candidates=documents.stream().filter(d->d!=document&&d.getDocumentType()==rule.parameters().peerDocumentType()).toList();
  if(group==null)return candidates.size()==1&&RuleFacts.read(candidates.get(0).getRuleFactsJson()).get(Field.DOCUMENT_PRESENTATION_GROUP)==null?candidates.get(0):null;
  var peers=candidates.stream().filter(d->group.equals(RuleFacts.read(d.getRuleFactsJson()).get(Field.DOCUMENT_PRESENTATION_GROUP))).toList();
  return peers.size()==1?peers.get(0):null;
 }
 private String peerEvidence(Rule rule,LcDocument document,List<LcDocument> documents){
  if(rule.parameters()==null||rule.parameters().peerDocumentType()==null)return "";
  try{
   var peer=uniquePeer(rule,document,documents);
   return peer==null?" · Gegen-Dokument fehlt oder ist nicht eindeutig":" · Gegen-Dokument "+peer.getId()+" / "+peer.getOriginalFilename()+" · Inhalts-SHA-256 "+RuleFacts.contentFingerprint(peer.getContent());
  }catch(RuntimeException invalid){return " · Gegen-Dokumentdaten ungültig";}
 }
 private Field peerSource(Field field){return switch(field){
  case PEER_AMOUNT->Field.DOCUMENT_AMOUNT;case PEER_CURRENCY->Field.DOCUMENT_CURRENCY;
  case PEER_SHIPMENT_DATE->Field.DOCUMENT_SHIPMENT_DATE;case PEER_GOODS_DESCRIPTION->Field.DOCUMENT_GOODS_DESCRIPTION;
  case PEER_QUANTITY->Field.DOCUMENT_QUANTITY;case PEER_QUANTITY_UNIT->Field.DOCUMENT_QUANTITY_UNIT;
  case PEER_NET_WEIGHT->Field.DOCUMENT_NET_WEIGHT;case PEER_GROSS_WEIGHT->Field.DOCUMENT_GROSS_WEIGHT;case PEER_WEIGHT_UNIT->Field.DOCUMENT_WEIGHT_UNIT;
  case PEER_CONSIGNEE->Field.DOCUMENT_CONSIGNEE;
  case PEER_DOCUMENT_NUMBER->Field.DOCUMENT_NUMBER;
  case PEER_PACKAGE_COUNT->Field.DOCUMENT_PACKAGE_COUNT;
  case PEER_SHIPPING_MARKS->Field.DOCUMENT_SHIPPING_MARKS;
  default->throw new IllegalArgumentException("Kein Peer-Feld.");
 };}
}
