package de.corporate.lc.rulepack;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class PackCodec {
 public static final int MAX_BYTES=512*1024;
 private final ObjectMapper json;
 private static final Pattern EXCLUDED=Pattern.compile("(?i)(\\bICC\\b|\\bUCP\\b|\\bISBP\\b|iccwbo|international chamber of commerce)");
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
  if(p==null||p.schemaVersion()!=1)bad("Schema-Version 1 erforderlich.");
  token(p.packId(),"[a-z][a-z0-9-]{2,30}","Pack-ID");
  token(p.version(),"[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}","Pack-Version");
  text(p.name(),100,"Name");text(p.license(),100,"Lizenz");text(p.rightsStatement(),500,"Rechteerklärung");
  if(!"OWN_INTERNAL".equals(p.origin()))bad("Nur eigene interne Rule Packs sind derzeit zulässig.");
  if(p.rules()==null||p.rules().isEmpty()||p.rules().size()>25)bad("1 bis 25 Regeln erforderlich.");
  if(p.tests()==null||p.tests().isEmpty()||p.tests().size()>150)bad("1 bis 150 synthetische Testfälle erforderlich.");
  var ids=new HashSet<String>();
  for(var r:p.rules()){
   if(r==null)bad("Leere Regel.");
   token(r.id(),"[a-z][a-z0-9-]{1,30}","Regel-ID");
   token(r.version(),"[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}","Regel-Version");
   if(!ids.add(r.id()))bad("Regel-ID doppelt.");
   text(r.message(),200,"Meldung");text(r.sourceReference(),160,"Interne Quellenreferenz");
   if(r.documentType()==null||r.left()==null||r.right()==null||r.operator()==null||r.severity()==null)bad("Regel ist unvollständig.");
   if(!r.left().document()||r.right().document()||!r.left().kind().equals(r.right().kind()))bad("Nur typgleiche Dokument-/LC-Vergleiche sind erlaubt.");
   if(r.left().kind().equals("TEXT")&&r.operator()!=PackDefinition.Operator.EQ&&r.operator()!=PackDefinition.Operator.NE)bad("Textfelder erlauben nur EQ oder NE.");
  }
  var names=new HashSet<String>();
  for(var t:p.tests()){
   if(t==null||t.expected()==null)bad("Testfall ist unvollständig.");
   text(t.name(),100,"Testname");if(!names.add(t.name()))bad("Testname doppelt.");
   if(!ids.contains(t.ruleId()))bad("Testfall verweist auf unbekannte Regel.");
   if(t.left()!=null&&t.left().length()>100||t.right()!=null&&t.right().length()>100)bad("Testwerte zu lang.");
  }
  for(var id:ids){
   var outcomes=EnumSet.noneOf(PackDefinition.Outcome.class);
   p.tests().stream().filter(t->id.equals(t.ruleId())).forEach(t->outcomes.add(t.expected()));
   if(outcomes.size()!=3)bad("Jede Regel benötigt PASS-, FAIL- und NOT_EVALUABLE-Testfälle.");
  }
 }
 private void text(String value,int max,String label){
  if(value==null||value.isBlank()||value.length()>max)bad(label+" fehlt oder ist zu lang.");
  if(EXCLUDED.matcher(value).find())bad("ICC-/UCP-/ISBP-bezogene Packs bleiben ausgeschlossen.");
 }
 private void token(String value,String pattern,String label){
  text(value,80,label);if(!value.matches(pattern))bad(label+" hat ein ungültiges Format.");
 }
 private void bad(String message){throw new IllegalArgumentException(message);}
}
