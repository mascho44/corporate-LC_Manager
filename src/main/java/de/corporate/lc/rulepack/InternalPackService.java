package de.corporate.lc.rulepack;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.check.api.*;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.lc.domain.LetterOfCredit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.util.*;
import static de.corporate.lc.rulepack.PackDefinition.*;

@Service
public class InternalPackService {
 private final PackCodec codec;private final PackVersionRepository versions;
 private final PackSelectionRepository selections;private final AuditService audit;
 public InternalPackService(PackCodec c,PackVersionRepository v,PackSelectionRepository s,AuditService a){codec=c;versions=v;selections=s;audit=a;}
 public record Preview(PackDefinition definition,String checksum,List<PackEvaluator.TestResult> tests,boolean testsPassed){}
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
   version.packId+" v"+version.version+" · SHA-256 "+version.checksum+" · Rechte und interne Freigabe bestätigt · vorher "+selected.previousVersionId);
 }
 @Transactional
 public void deactivate(String packId,Authentication auth){
  var selected=selections.locked(packId).orElseThrow();
  if(selected.activeVersionId==null)return;
  var old=selected.activeVersionId;selected.previousVersionId=old;selected.activeVersionId=null;
  audit.recordInTransaction(auth,"LC_RULE_PACK_DEACTIVATED","RULE_PACK",packId,"Deaktivierte Version "+old);
 }
 private PackDefinition read(StoredPackVersion v){
  if(!codec.digest(v.definitionJson).equals(v.checksum))throw new IllegalStateException("Prüfsumme des Rule Packs stimmt nicht.");
  var pack=codec.parse(v.definitionJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  if(!pack.packId().equals(v.packId)||!pack.version().equals(v.version))throw new IllegalStateException("Pack-Identität stimmt nicht.");
  return pack;
 }
 @Transactional(readOnly=true)
 public List<CheckResult> evaluate(LetterOfCredit lc,List<LcDocument> documents){
  var findings=new ArrayList<CheckResult>();
  for(var selection:selections.findAll().stream().sorted(Comparator.comparing(s->s.id)).toList()){
   if(selection.activeVersionId==null)continue;
   var stored=versions.findById(selection.activeVersionId).orElseThrow();
   PackDefinition definition;
   try{definition=read(stored);if(!stored.testsPassed)throw new IllegalStateException("Pack wurde nicht erfolgreich getestet.");}
   catch(RuntimeException invalid){
    findings.add(new CheckResult(CheckResult.Severity.WARNING,"INTERNAL_PACK_INVALID",
     "Aktives internes Rule Pack konnte nicht ausgewertet werden. Administration informieren.",
     "Pack "+selection.id,null,"Version "+selection.activeVersionId));continue;
   }
   for(var rule:definition.rules()){
    String code="PACK."+definition.packId()+"."+rule.id();
    var metadata=new RuleDefinition(code,definition.version()+"/"+rule.version(),definition.name()+" · "+rule.id(),
     "Internes Pack "+definition.packId()+" v"+definition.version()+" · "+rule.sourceReference(),
     rule.message(),"Deklarativer Metadatenvergleich. Keine vollständige Dokumentenprüfung oder Rechtsfreigabe.",null);
    var matching=documents.stream().filter(d->d.getDocumentType()==rule.documentType()).toList();
    if(matching.isEmpty()){
     findings.add(new CheckResult(CheckResult.Severity.WARNING,code,"Interne Pack-Regel nicht prüfbar: passendes Dokument fehlt.",
      metadata.basis(),null,"Erforderlicher Typ: "+rule.documentType()+" · SHA-256 "+stored.checksum).withRule(metadata));
    }
    for(var document:matching){
     String left=value(rule.left(),lc,document),right=value(rule.right(),lc,document);
     var outcome=PackEvaluator.compare(rule,left,right);
     var level=outcome==Outcome.PASS?CheckResult.Severity.OK:outcome==Outcome.NOT_EVALUABLE||rule.severity()==Level.WARNING?CheckResult.Severity.WARNING:CheckResult.Severity.DISCREPANCY;
     findings.add(new CheckResult(level,code,(outcome==Outcome.PASS?"Interne Regel erfüllt: ":outcome==Outcome.FAIL?"Interne Regel verletzt: ":"Interne Regel nicht prüfbar: ")+rule.message(),
      metadata.basis()+" · "+rule.right()+" = "+Objects.toString(right,"nicht erfasst"),
      document.getOriginalFilename(),rule.left()+" = "+Objects.toString(left,"nicht erfasst")+" · "+rule.operator()+" · SHA-256 "+stored.checksum).withRule(metadata));
    }
   }
  }
  return List.copyOf(findings);
 }
 private String value(Field field,LetterOfCredit lc,LcDocument doc){
  Object value=switch(field){
   case DOCUMENT_AMOUNT->doc.getAmount();case DOCUMENT_CURRENCY->doc.getCurrency();case DOCUMENT_DATE->doc.getDocumentDate();
   case LC_AMOUNT->lc.getAmount();case LC_CURRENCY->lc.getCurrency();case LC_EXPIRY_DATE->lc.getExpiryDate();case LC_LATEST_SHIPMENT_DATE->lc.getLatestShipmentDate();
  };
  return value==null?null:value instanceof java.math.BigDecimal number?number.toPlainString():value.toString();
 }
}
