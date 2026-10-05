package de.corporate.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import static de.corporate.lc.rulepack.PackDefinition.Field;

/** Explicitly reviewed supplementary data, never guessed from OCR or a filename. */
public final class RuleFacts {
 private RuleFacts(){}
 private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
 public static final int MAX_BYTES=32*1024;
 public static final Set<Field> DOCUMENT=Set.of(Field.DOCUMENT_ISSUER,Field.DOCUMENT_RECIPIENT,Field.DOCUMENT_GOODS_DESCRIPTION);
 public static final Set<Field> LC=Set.of(Field.LC_RULE_STANDARD,Field.LC_TRANSFERRED,Field.LC_SECOND_BENEFICIARY,Field.LC_GOODS_DESCRIPTION);
 public static Map<Field,String> read(String source){
  if(source==null||source.isBlank())return Map.of();
  try{return JSON.readValue(source,new TypeReference<EnumMap<Field,String>>(){});}
  catch(Exception invalid){throw new IllegalStateException("Gespeicherte Prüfdaten sind ungültig.",invalid);}
 }
 public static String encode(Map<Field,String> facts,boolean document){
  if(facts==null||facts.size()>10)throw new IllegalArgumentException("Ungültige Prüfdaten.");
  var values=new EnumMap<Field,String>(Field.class);
  for(var e:facts.entrySet()){
   if(!(document?DOCUMENT:LC).contains(e.getKey()))throw new IllegalArgumentException("Dieses Prüffeld ist hier nicht zulässig.");
   String value=e.getValue();
   if(value==null||value.isBlank())continue;
   int max=e.getKey().name().contains("GOODS_DESCRIPTION")?4000:500;
   if(value.length()>max)throw new IllegalArgumentException("Prüfwert ist zu lang.");
   value=value.strip();
   if(e.getKey()==Field.LC_TRANSFERRED&&!Set.of("true","false").contains(value))throw new IllegalArgumentException("Übertragungsstatus muss true oder false sein.");
   if(e.getKey()==Field.LC_RULE_STANDARD&&!Set.of("UCP600","OTHER").contains(value))throw new IllegalArgumentException("Regelstandard muss UCP600 oder OTHER sein.");
   values.put(e.getKey(),value);
  }
  try{return JSON.writeValueAsString(values);}catch(Exception invalid){throw new IllegalStateException(invalid);}
 }
 public static Map<Field,String> decodeRequest(byte[] source){
  if(source.length>MAX_BYTES)throw new IllegalArgumentException("Prüfdaten überschreiten 32 KB.");
  try{
   var root=JSON.readTree(source);
   if(root==null||!root.isObject()||root.size()>10)throw new IllegalArgumentException("Prüfdaten müssen ein JSON-Objekt mit höchstens zehn Feldern sein.");
   var facts=new EnumMap<Field,String>(Field.class);
   var fields=root.fields();
   while(fields.hasNext()){
    var entry=fields.next();var value=entry.getValue();
    if(!value.isNull()&&!value.isTextual())throw new IllegalArgumentException("Prüfwerte müssen Zeichenketten oder null sein.");
    facts.put(Field.valueOf(entry.getKey()),value.isNull()?null:value.textValue());
   }
   return facts;
  }catch(IllegalArgumentException invalid){throw invalid;}
  catch(Exception invalid){throw new IllegalArgumentException("Ungültige Prüfdaten oder doppelte JSON-Felder.");}
 }
 public static String fingerprint(String source){
  try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Objects.toString(source,"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
  catch(Exception invalid){throw new IllegalStateException(invalid);}
 }
}
