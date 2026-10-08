package de.ostms.lc.rulepack;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Read-only capability inspection. A specification is never stored or activated as a pack. */
final class RuleSpecificationInspector {
 private RuleSpecificationInspector(){}
 record Issue(String ruleId,String reason){}
 record Report(String kind,int schemaVersion,int rules,int supported,List<Issue> issues,List<String> configurationRequirements,boolean importable,String message){}
 static Report inspect(JsonNode root,ObjectMapper json,PackCodec codec){
  if(!root.isObject()||!root.path("specVersion").isTextual()||!root.path("rules").isArray())throw new IllegalArgumentException("Ungültige Erweiterungsspezifikation.");
  var rules=root.path("rules");if(rules.isEmpty()||rules.size()>PackCodec.MAX_RULES)throw new IllegalArgumentException("Spezifikation benötigt 1 bis "+PackCodec.MAX_RULES+" Regeln.");
  var issues=new ArrayList<Issue>();var configuration=new TreeSet<String>();int supported=0;var ids=new HashSet<String>();
  for(var node:rules){
   String id=node.path("id").asText("");if(id.length()>100)id=id.substring(0,100);
   try{
    if(!node.isObject())throw new IllegalArgumentException("Regel muss ein Objekt sein.");
    ObjectNode copy=((ObjectNode)node).deepCopy();copy.remove(List.of("status","requires"));
    if(copy.path("parameters").hasNonNull("calendarId")){
     String calendar=copy.path("parameters").path("calendarId").asText();
     if(!calendar.matches("[a-z][a-z0-9-]{2,30}"))throw new IllegalArgumentException("Ungültige Kalender-ID.");
     configuration.add("Bankkalender erforderlich: "+calendar);
     ((ObjectNode)copy.path("parameters")).remove("calendarId");
    }
    var rule=json.treeToValue(copy,PackDefinition.Rule.class);
    var pack=new PackDefinition(5,"capability-inspection","0.0.0","Capability inspection","OWN_INTERNAL","Not specified","Read-only structural inspection",List.of(rule),List.of());
    codec.validateCapabilities(pack);
    if(!ids.add(rule.id()))throw new IllegalArgumentException("Regel-ID doppelt.");
    supported++;
   }catch(Exception invalid){
    String reason=invalid instanceof IllegalArgumentException?invalid.getMessage():"Unbekannte Felder, Dokumenttypen oder ungültige Regelstruktur.";
    issues.add(new Issue(id,reason));
   }
  }
  return new Report("SPECIFICATION",5,rules.size(),supported,List.copyOf(issues),List.copyOf(configuration),false,
   "Nur Kompatibilitätsprüfung: kein importierbares Pack. Pack-Metadaten, Rechteerklärung und fachlich freigegebene Tests für alle Regeln erforderlich.");
 }
}
