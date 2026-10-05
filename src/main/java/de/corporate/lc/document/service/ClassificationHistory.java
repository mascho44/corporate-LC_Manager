package de.corporate.lc.document.service;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.corporate.lc.document.domain.DocumentType;
import java.time.Instant;
public final class ClassificationHistory {
 private static final ObjectMapper JSON=new ObjectMapper();
 private ClassificationHistory(){}
 public static String automatic(String filename,String text){var classification=DocumentClassifier.classify(filename,text);var history=JSON.createArrayNode();var event=JSON.valueToTree(classification);((com.fasterxml.jackson.databind.node.ObjectNode)event).put("action","AUTOMATIC").put("at",Instant.now().toString());history.add(event);return history.toString();}
 public static String manual(String history,DocumentType type,String actor){
  ArrayNode events=read(history);var event=JSON.createObjectNode();event.put("action","MANUAL").put("selectedType",type.name()).put("actor",actor).put("at",Instant.now().toString());events.add(event);return events.toString();
 }
 public static ArrayNode read(String history){if(history==null||history.isBlank())return JSON.createArrayNode();try{var node=JSON.readTree(history);if(!node.isArray())throw new IllegalArgumentException("Ungültige Klassifikationshistorie");return (ArrayNode)node;}catch(java.io.IOException e){throw new IllegalStateException("Klassifikationshistorie konnte nicht gelesen werden",e);}}
 public static String actor(){var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();return auth==null?"unknown":auth.getName();}
 public static DocumentClassifier.Classification suggestion(String history){
  for(var event:read(history))if("AUTOMATIC".equals(event.path("action").asText())){
   java.util.List<String> evidence=new java.util.ArrayList<>();event.path("evidence").forEach(value->evidence.add(value.asText()));
   return new DocumentClassifier.Classification(event.path("suggestedType").isNull()?null:DocumentType.valueOf(event.path("suggestedType").asText()),event.path("score").asDouble(),event.path("status").asText(),event.path("method").asText(),java.util.List.copyOf(evidence));
  }return null;
 }
}
