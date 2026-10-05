package de.corporate.lc.rulepack;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class PackCodec {
 public static final int MAX_BYTES=512*1024;
 private final ObjectMapper json;
 public PackCodec(ObjectMapper mapper){json=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);}
 public PackDefinition parse(byte[] bytes){
  if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Rule Pack überschreitet 512 KB.");
  try{var pack=json.readValue(bytes,PackDefinition.class);validate(pack);return pack;}
  catch(IllegalArgumentException e){throw e;}
  catch(Exception e){throw new IllegalArgumentException("Ungültiges Rule-Pack-JSON oder unbekannte Felder.");}
 }
 public String canonical(PackDefinition pack){
  try{return json.writeValueAsString(pack);}catch(Exception e){throw new IllegalStateException("Pack konnte nicht serialisiert werden.");}
 }
 public String digest(String source){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 public void validate(PackDefinition p){
  if(p==null||(p.schemaVersion()!=1&&p.schemaVersion()!=2))bad("Schema-Version 1 oder 2 erforderlich.");
  token(p.packId(),"[a-z][a-z0-9-]{2,30}","Pack-ID");
  token(p.version(),"[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}","Pack-Version");
  text(p.name(),100,"Name");text(p.license(),100,"Lizenz");text(p.rightsStatement(),500,"Rechteerklärung");
  if(!"OWN_INTERNAL".equals(p.origin())&&!"ICC_LICENSED".equals(p.origin()))bad("Herkunft muss OWN_INTERNAL oder ICC_LICENSED sein.");
  if(p.rules()==null||p.rules().isEmpty()||p.rules().size()>25)bad("1 bis 25 Regeln erforderlich.");
  if(p.tests()==null||p.tests().isEmpty()||p.tests().size()>150)bad("1 bis 150 synthetische Testfälle erforderlich.");
  var ids=new HashSet<String>();
  for(var r:p.rules()){
   if(r==null)bad("Leere Regel.");
   token(r.id(),"[a-z][a-z0-9-]{1,30}","Regel-ID");
   token(r.version(),"[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}","Regel-Version");
   if(!ids.add(r.id()))bad("Regel-ID doppelt.");
   text(r.message(),200,"Meldung");text(r.sourceReference(),160,"Quellenreferenz");
   if(r.documentType()==null||r.left()==null||r.right()==null||r.operator()==null||r.severity()==null)bad("Regel ist unvollständig.");
   if(!r.left().document()||r.right().document()||!r.left().kind().equals(r.right().kind()))bad("Nur typgleiche Dokument-/LC-Vergleiche sind erlaubt.");
   if(r.left()==PackDefinition.Field.DOCUMENT_GOODS_DESCRIPTION&&r.effectiveMode()!=PackDefinition.Mode.MANUAL)bad("Warenbeschreibungen benötigen eine manuelle fachliche Prüfung.");
   boolean pair=switch(r.left()){
    case DOCUMENT_ISSUER->r.right()==PackDefinition.Field.LC_BENEFICIARY||r.right()==PackDefinition.Field.LC_SECOND_BENEFICIARY;
    case DOCUMENT_RECIPIENT->r.right()==PackDefinition.Field.LC_APPLICANT;
    case DOCUMENT_GOODS_DESCRIPTION->r.right()==PackDefinition.Field.LC_GOODS_DESCRIPTION;
    default->true;
   };
   if(!pair)bad("Unzulässige Kombination von Prüffeldern.");
   if(Set.of("TEXT","CURRENCY","BOOLEAN").contains(r.left().kind())&&r.operator()!=PackDefinition.Operator.EQ&&r.operator()!=PackDefinition.Operator.NE)bad("Textfelder erlauben nur EQ oder NE.");
   if(p.schemaVersion()==1&&(r.mode()!=null||r.conditions()!=null||r.left().ordinal()>6||r.right().ordinal()>6))bad("Erweiterte Regeln benötigen Schema-Version 2.");
   if(r.conditions()!=null){
    if(r.conditions().size()>8)bad("Maximal acht Anwendungsbedingungen erlaubt.");
    for(var c:r.conditions()){
     if(c==null||c.field()==null||(c.operator()!=PackDefinition.Operator.EQ&&c.operator()!=PackDefinition.Operator.NE))bad("Bedingungen benötigen ein Feld und EQ oder NE.");
     text(c.value(),100,"Bedingungswert");
     var probe=new PackDefinition.Rule("probe","1.0.0",r.documentType(),c.field(),PackDefinition.Operator.EQ,c.field(),r.severity(),"probe","probe");
     if(PackEvaluator.compare(probe,c.value(),c.value())!=PackDefinition.Outcome.PASS)bad("Bedingungswert hat ein ungültiges Format.");
    }
   }
  }
  var names=new HashSet<String>();
  for(var t:p.tests()){
   if(t==null||t.expected()==null)bad("Testfall ist unvollständig.");
   if(p.schemaVersion()==1&&t.expected().ordinal()>2)bad("Erweiterte Testergebnisse benötigen Schema 2.");
   text(t.name(),100,"Testname");if(!names.add(t.name()))bad("Testname doppelt.");
   if(!ids.contains(t.ruleId()))bad("Testfall verweist auf unbekannte Regel.");
   if(t.left()!=null&&t.left().length()>100||t.right()!=null&&t.right().length()>100)bad("Testwerte zu lang.");
   if(t.facts()!=null){
    if(p.schemaVersion()!=2||t.facts().size()>20)bad("Test-Prüfdaten benötigen Schema 2 und maximal 20 Felder.");
    var rule=p.rules().stream().filter(r->r.id().equals(t.ruleId())).findFirst().orElseThrow();
    if(t.facts().containsKey(rule.left())||t.facts().containsKey(rule.right()))bad("Vergleichswerte dürfen nicht zusätzlich unter facts stehen.");
    for(var entry:t.facts().entrySet())if(entry.getKey()==null||entry.getValue()!=null&&entry.getValue().length()>100)bad("Ungültige Test-Prüfdaten.");
   }
  }
  for(var id:ids){
   var outcomes=EnumSet.noneOf(PackDefinition.Outcome.class);
   p.tests().stream().filter(t->id.equals(t.ruleId())).forEach(t->outcomes.add(t.expected()));
   var rule=p.rules().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
   var required=rule.effectiveMode()==PackDefinition.Mode.MANUAL
    ?EnumSet.of(PackDefinition.Outcome.MANUAL_REVIEW,PackDefinition.Outcome.NOT_EVALUABLE)
    :EnumSet.of(PackDefinition.Outcome.PASS,PackDefinition.Outcome.FAIL,PackDefinition.Outcome.NOT_EVALUABLE);
   if(rule.conditions()!=null&&!rule.conditions().isEmpty()){required.add(PackDefinition.Outcome.NOT_APPLICABLE);required.add(PackDefinition.Outcome.NOT_EVALUABLE);}
   if(!outcomes.containsAll(required))bad("Testfälle müssen Erfolg, Fehler, fehlende Daten sowie Anwendbarkeit der jeweiligen Regel abdecken.");
   if(rule.conditions()!=null&&!rule.conditions().isEmpty()){
    boolean missingScope=p.tests().stream().filter(t->id.equals(t.ruleId())&&t.expected()==PackDefinition.Outcome.NOT_EVALUABLE).anyMatch(t->{
     var facts=PackEvaluator.testFacts(rule,t);
     return rule.conditions().stream().anyMatch(c->facts.get(c.field())==null||facts.get(c.field()).isBlank())
      &&PackEvaluator.evaluate(rule,facts)==PackDefinition.Outcome.NOT_EVALUABLE;
    });
    if(!missingScope)bad("Bedingte Regeln benötigen einen Test mit fehlenden Anwendungsdaten.");
   }
  }
 }
 private void text(String value,int max,String label){
  if(value==null||value.isBlank()||value.length()>max)bad(label+" fehlt oder ist zu lang.");
 }
 private void token(String value,String pattern,String label){
  text(value,80,label);if(!value.matches(pattern))bad(label+" hat ein ungültiges Format.");
 }
 private void bad(String message){throw new IllegalArgumentException(message);}
}
