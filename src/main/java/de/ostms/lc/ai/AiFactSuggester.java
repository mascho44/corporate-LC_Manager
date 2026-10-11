package de.ostms.lc.ai;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.rulepack.DocumentFactSuggester;
import de.ostms.lc.rulepack.RuleFacts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/**
 * Proposes text facts with a language model. Every value must come with a verbatim quote that really occurs in the document and contain the value;
 * anything else is discarded, so a model cannot invent facts. Proposals are never stored before a person confirms them.
 */
@Service
public class AiFactSuggester {
 private static final ObjectMapper JSON=new ObjectMapper();
 static final Map<String,Field> ASKED=new LinkedHashMap<>();
 static{
  ASKED.put("document_number",Field.DOCUMENT_NUMBER);ASKED.put("vessel",Field.DOCUMENT_VESSEL);ASKED.put("carrier",Field.DOCUMENT_CARRIER);
  ASKED.put("consignee",Field.DOCUMENT_CONSIGNEE);ASKED.put("notify_party",Field.DOCUMENT_NOTIFY_PARTY);ASKED.put("loading_port",Field.DOCUMENT_LOADING_PORT);
  ASKED.put("discharge_port",Field.DOCUMENT_DISCHARGE_PORT);ASKED.put("signer_name",Field.DOCUMENT_SIGNER_NAME);ASKED.put("signer_role",Field.DOCUMENT_SIGNER_ROLE);
  ASKED.put("insured_party",Field.DOCUMENT_INSURED_PARTY);ASKED.put("drawee",Field.DOCUMENT_DRAWEE);ASKED.put("shipping_marks",Field.DOCUMENT_SHIPPING_MARKS);
 }
 private static final int MAX_TEXT=12000;
 private final LlmClient client;private final DocumentFactSuggester rules;
 @Autowired public AiFactSuggester(@Autowired(required=false) LlmClient client,@Autowired(required=false) DocumentFactSuggester rules){this.client=client;this.rules=rules;}
 public boolean enabled(){return client!=null;}
 public String model(){return client==null?null:client.model();}

 public List<DocumentFactSuggester.Suggestion> suggest(LcDocument doc)throws IOException{
  if(client==null)return List.of();
  String text=doc.getExtractedText()==null?"":doc.getExtractedText();
  if(text.isBlank())return List.of();
  var known=new HashSet<Field>(RuleFacts.read(doc.getRuleFactsJson()).keySet());
  if(rules!=null)rules.suggest(doc).forEach(s->known.add(s.field()));
  var wanted=new ArrayList<String>();ASKED.forEach((key,field)->{if(!known.contains(field))wanted.add(key);});
  if(wanted.isEmpty())return List.of();
  String clipped=text.length()>MAX_TEXT?text.substring(0,MAX_TEXT):text;
  return verify(client.generateJson(prompt(wanted,clipped)),wanted,clipped,client.model(),RuleFacts.read(doc.getRuleFactsJson()));
 }
 static String prompt(List<String> wanted,String text){
  return "Extract the following fields from the document text. Reply with JSON only. For every field return an object {\"value\": ..., \"quote\": exact verbatim quote from the text that contains the value}. "
   +"If a field is not in the text return {\"value\": null, \"quote\": null}. Never guess and never translate.\nFields: "+wanted+"\nDocument:\n"+text;
 }
 static List<DocumentFactSuggester.Suggestion> verify(String answer,List<String> wanted,String text,String model,Map<Field,String> current){
  var out=new ArrayList<DocumentFactSuggester.Suggestion>();
  JsonNode root;try{root=JSON.readTree(answer);}catch(IOException invalid){return out;}
  String haystack=norm(text);
  for(String key:wanted){
   JsonNode node=root.path(key);if(!node.isObject())continue;
   String value=node.path("value").isTextual()?node.path("value").asText().trim():null,quote=node.path("quote").isTextual()?node.path("quote").asText().trim():null;
   if(value==null||quote==null||value.isEmpty()||value.length()>200||quote.length()<value.length())continue;
   String nq=norm(quote),nv=norm(value);
   if(nv.isEmpty()||!haystack.contains(nq)||!nq.contains(nv))continue;
   Field field=ASKED.get(key);if(Objects.equals(value,current.get(field)))continue;
   String shown=quote.length()>160?quote.substring(0,160)+"…":quote;
   out.add(new DocumentFactSuggester.Suggestion(field,value,"KI-Vorschlag ("+model+"), Zitat: „"+shown+"“ – bitte prüfen",current.get(field)));
  }
  return out;
 }
 /** Lower case with every run of non-alphanumeric characters collapsed, so line breaks and punctuation do not matter for the quote check. */
 static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
}
