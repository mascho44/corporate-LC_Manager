package de.ostms.lc.ai;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.rulepack.DocumentFactSuggester;
import de.ostms.lc.rulepack.RuleFacts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/**
 * Proposes document facts with a language model, whichever provider is configured. Only fields that make sense for the document type are asked.
 * Every value needs a verbatim quote that really occurs in the document and supports the value (text contained, number or date equal after normalising);
 * anything else is discarded, so a model cannot invent facts. Proposals are never stored before a person confirms them.
 */
@Service
public class AiFactSuggester {
 private static final ObjectMapper JSON=new ObjectMapper();
 enum Kind{TEXT,NUMBER,DATE,CURRENCY}
 record Spec(String key,Field field,Kind kind,String hint){}
 private static final Set<DocumentType> TRANSPORT=EnumSet.of(DocumentType.BILL_OF_LADING,DocumentType.SEA_WAYBILL,DocumentType.CHARTER_PARTY_BILL_OF_LADING,DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT,DocumentType.AIR_WAYBILL);
 private static Spec t(String k,Field f,String h){return new Spec(k,f,Kind.TEXT,h);}
 private static Spec n(String k,Field f,String h){return new Spec(k,f,Kind.NUMBER,h);}
 private static final List<Spec> TRANSPORT_SPECS=List.of(t("vessel",Field.DOCUMENT_VESSEL,"vessel name"),t("carrier",Field.DOCUMENT_CARRIER,"carrier"),
  t("consignee",Field.DOCUMENT_CONSIGNEE,"consignee"),t("notify_party",Field.DOCUMENT_NOTIFY_PARTY,"notify party"),t("loading_port",Field.DOCUMENT_LOADING_PORT,"port of loading"),t("discharge_port",Field.DOCUMENT_DISCHARGE_PORT,"port of discharge"),
  t("signer_name",Field.DOCUMENT_SIGNER_NAME,"name of the person who signed"),t("signer_role",Field.DOCUMENT_SIGNER_ROLE,"role of the signer, e.g. master or agent"),t("shipping_marks",Field.DOCUMENT_SHIPPING_MARKS,"marks and numbers"),
  new Spec("on_board_date",Field.DOCUMENT_ON_BOARD_DATE,Kind.DATE,"date shipped on board, ISO format YYYY-MM-DD"),n("gross_weight",Field.DOCUMENT_GROSS_WEIGHT,"gross weight as a plain number with decimal point"),n("original_count",Field.DOCUMENT_ORIGINAL_COUNT,"number of original bills, as a digit"));
 private static final Map<DocumentType,List<Spec>> SPECS=new EnumMap<>(DocumentType.class);
 static{
  for(var type:TRANSPORT)SPECS.put(type,TRANSPORT_SPECS);
  SPECS.put(DocumentType.COMMERCIAL_INVOICE,List.of(n("quantity",Field.DOCUMENT_QUANTITY,"total quantity as a plain number"),
   n("unit_price",Field.DOCUMENT_UNIT_PRICE_AMOUNT,"unit price as a plain number"),n("gross_weight",Field.DOCUMENT_GROSS_WEIGHT,"gross weight as a plain number"),n("net_weight",Field.DOCUMENT_NET_WEIGHT,"net weight as a plain number"),
   new Spec("shipment_date",Field.DOCUMENT_SHIPMENT_DATE,Kind.DATE,"shipment date, ISO format YYYY-MM-DD"),t("vessel",Field.DOCUMENT_VESSEL,"vessel name if stated")));
  SPECS.put(DocumentType.PACKING_LIST,List.of(n("gross_weight",Field.DOCUMENT_GROSS_WEIGHT,"total gross weight as a plain number"),n("net_weight",Field.DOCUMENT_NET_WEIGHT,"total net weight as a plain number"),
   n("package_count",Field.DOCUMENT_PACKAGE_COUNT,"number of packages"),t("shipping_marks",Field.DOCUMENT_SHIPPING_MARKS,"marks and numbers")));
  SPECS.put(DocumentType.INSURANCE_CERTIFICATE,List.of(t("insured_party",Field.DOCUMENT_INSURED_PARTY,"the insured party"),
   n("insured_amount",Field.DOCUMENT_INSURED_AMOUNT,"insured amount as a plain number"),new Spec("insurance_currency",Field.DOCUMENT_INSURANCE_CURRENCY,Kind.CURRENCY,"currency of the insured amount, ISO code"),
   t("insurance_risks",Field.DOCUMENT_INSURANCE_RISKS,"risks covered, e.g. clauses"),new Spec("effective_date",Field.DOCUMENT_INSURANCE_EFFECTIVE_DATE,Kind.DATE,"start of cover, ISO format YYYY-MM-DD")));
  SPECS.put(DocumentType.BILL_OF_EXCHANGE,List.of(t("drawee",Field.DOCUMENT_DRAWEE,"the drawee (who must pay)"),n("tenor_days",Field.DOCUMENT_DRAFT_TENOR_DAYS,"days after which payable, 0 for sight"),t("tenor_basis",Field.DOCUMENT_DRAFT_TENOR_BASIS,"what the tenor counts from")));
  SPECS.put(DocumentType.CERTIFICATE_OF_ORIGIN,List.of(t("origin_country",Field.DOCUMENT_ORIGIN_COUNTRY,"country of origin")));
  SPECS.put(DocumentType.BENEFICIARY_CERTIFICATE,List.of(t("signer_name",Field.DOCUMENT_SIGNER_NAME,"name of the signer"),t("signer_role",Field.DOCUMENT_SIGNER_ROLE,"role of the signer")));
 }
 private static final int MAX_TEXT=12000;
 private final LlmClientProvider provider;private final DocumentFactSuggester rules;
 @Autowired public AiFactSuggester(LlmClientProvider provider,@Autowired(required=false) DocumentFactSuggester rules){this.provider=provider;this.rules=rules;}
 public boolean available(){return provider.current().isPresent();}
 public String provider(){return provider.current().map(LlmClient::provider).orElse(null);}
 public String model(){return provider.current().map(LlmClient::model).orElse(null);}

 public List<DocumentFactSuggester.Suggestion> suggest(LcDocument doc)throws IOException{
  var client=provider.current().orElse(null);
  if(client==null)return List.of();
  String text=doc.getExtractedText()==null?"":doc.getExtractedText();
  if(text.isBlank()||doc.getDocumentType()==null)return List.of();
  var current=RuleFacts.read(doc.getRuleFactsJson());
  var known=new HashSet<Field>(current.keySet());
  if(rules!=null)rules.suggest(doc).forEach(s->known.add(s.field()));
  var wanted=specsFor(doc.getDocumentType(),known);
  if(wanted.isEmpty())return List.of();
  String clipped=text.length()>MAX_TEXT?text.substring(0,MAX_TEXT):text;
  return verify(client.generateJson(prompt(wanted,clipped)),wanted,clipped,client.provider()+"/"+client.model(),current);
 }
 /** The fields worth asking for this document type that nobody has filled or proposed yet. */
 static List<Spec> specsFor(DocumentType type,Set<Field> known){
  var out=new ArrayList<Spec>();for(var s:SPECS.getOrDefault(type,List.of()))if(!known.contains(s.field()))out.add(s);return out;
 }
 static String prompt(List<Spec> wanted,String text){
  var fields=new StringBuilder();for(var s:wanted)fields.append("- ").append(s.key()).append(": ").append(s.hint()).append('\n');
  return "Extract the following fields from the document text (OCR text, may contain recognition errors). Reply with JSON only. For every field return an object {\"value\": ..., \"quote\": exact verbatim quote from the text that contains the value}. "
   +"If a field is not stated in the text return {\"value\": null, \"quote\": null}. Never guess, never translate, and do not take a value from an unrelated field.\nFields:\n"+fields+"Document:\n"+text;
 }
 static List<DocumentFactSuggester.Suggestion> verify(String answer,List<Spec> wanted,String text,String source,Map<Field,String> current){
  var out=new ArrayList<DocumentFactSuggester.Suggestion>();
  JsonNode root;try{root=JSON.readTree(answer);}catch(IOException invalid){return out;}
  String haystack=norm(text);
  for(var spec:wanted){
   JsonNode node=root.path(spec.key());if(!node.isObject())continue;
   String value=scalar(node.path("value")),quote=scalar(node.path("quote"));
   if(value==null||quote==null||value.isEmpty()||value.length()>300||quote.isEmpty())continue;
   String nq=norm(quote);if(nq.isEmpty()||!haystack.contains(nq))continue;
   String accepted=accept(spec.kind(),value,quote);if(accepted==null)continue;
   try{RuleFacts.encode(Map.of(spec.field(),accepted),true);}catch(RuntimeException invalid){continue;}
   if(Objects.equals(accepted,current.get(spec.field())))continue;
   String shown=quote.length()>160?quote.substring(0,160)+"…":quote;
   out.add(new DocumentFactSuggester.Suggestion(spec.field(),accepted,"KI-Vorschlag ("+source+"), Zitat: „"+shown.replaceAll("\\s+"," ")+"“ – bitte prüfen",current.get(spec.field())));
  }
  return out;
 }
 private static String scalar(JsonNode n){return n.isTextual()?n.asText().trim():n.isNumber()?n.asText():null;}
 /** The normalised value if the quote supports it, else null. */
 static String accept(Kind kind,String value,String quote){
  switch(kind){
   case TEXT:{String nv=norm(value);return !nv.isEmpty()&&norm(quote).contains(nv)?value:null;}
   case CURRENCY:{String c=value.toUpperCase(Locale.ROOT);return c.matches("[A-Z]{3}")&&quote.toUpperCase(Locale.ROOT).contains(c)?c:null;}
   case NUMBER:{
    String n=DocumentFactSuggester.normalizeNumber(value.replaceAll("[^\\d.,]",""));if(n==null)return null;
    var m=Pattern.compile("\\d[\\d.,]*\\d|\\d").matcher(quote);
    while(m.find()){String q=DocumentFactSuggester.normalizeNumber(m.group());if(q!=null&&numeric(q).equals(numeric(n)))return n;}
    for(String w:norm(quote).split(" "))if(n.equals(WORDS.get(w)))return n;
    return null;
   }
   case DATE:{
    LocalDate wanted;try{wanted=LocalDate.parse(value.trim());}catch(RuntimeException e){return null;}
    for(var d:dates(quote))if(d.equals(wanted))return wanted.toString();
    return null;
   }
  }
  return null;
 }
 private static final Map<String,String> WORDS=Map.of("one","1","two","2","three","3","four","4","five","5","six","6","seven","7","eight","8","nine","9","ten","10");
 private static String numeric(String n){return new java.math.BigDecimal(n).stripTrailingZeros().toPlainString();}
 private static final List<DateTimeFormatter> FORMATS=List.of(DateTimeFormatter.ofPattern("d.M.uuuu"),DateTimeFormatter.ofPattern("d/M/uuuu"),DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("d MMM uuuu",Locale.ENGLISH),DateTimeFormatter.ofPattern("d MMMM uuuu",Locale.ENGLISH),DateTimeFormatter.ofPattern("uuuu.M.d"),DateTimeFormatter.ofPattern("yyMMdd"));
 static List<LocalDate> dates(String text){
  var out=new ArrayList<LocalDate>();
  var m=Pattern.compile("\\d{1,2}[./]\\d{1,2}[./]\\d{4}|\\d{4}[-.]\\d{1,2}[-.]\\d{1,2}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}|\\b\\d{6}\\b").matcher(text);
  while(m.find()){String raw=m.group().replaceAll("\\s+"," ");for(var f:FORMATS){try{out.add(LocalDate.parse(raw,f));break;}catch(RuntimeException ignored){}}}
  return out;
 }
 /** Lower case with every run of non-alphanumeric characters collapsed, so line breaks and punctuation do not matter for the quote check. */
 static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
}
