package de.ostms.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import de.ostms.lc.document.domain.DocumentType;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;
public final class RuleRequirements {
 private RuleRequirements(){}
 private static final ObjectMapper JSON=new ObjectMapper();
 public static Map<DocumentType,Map<Field,String>> read(String value){
  if(value==null||value.isBlank())return Map.of();
  try{return JSON.readValue(value,new TypeReference<EnumMap<DocumentType,Map<Field,String>>>(){});}
  catch(Exception invalid){throw new IllegalStateException("Dokumenttyp-Anforderungen sind ungültig.");}
 }
 public static String update(String previous,DocumentType type,Map<Field,String> fields){
  var values=new EnumMap<DocumentType,Map<Field,String>>(DocumentType.class);values.putAll(read(previous));
  values.put(type,RuleFacts.read(RuleFacts.encodeRequirements(fields)));
  try{return JSON.writeValueAsString(values);}catch(Exception invalid){throw new IllegalStateException(invalid);}
 }
}
