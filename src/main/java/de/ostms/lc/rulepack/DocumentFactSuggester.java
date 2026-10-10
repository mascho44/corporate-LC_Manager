package de.ostms.lc.rulepack;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.service.DocumentDateDetector;
import de.ostms.lc.document.service.SignatureEvidenceService;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** Proposes document facts from OCR/signature evidence. Proposals are never stored until a user confirms them. */
@Service
public class DocumentFactSuggester {
 public record Suggestion(Field field,String value,String source,String current){}
 private static final Set<DocumentType> TRANSPORT=EnumSet.of(DocumentType.BILL_OF_LADING,DocumentType.SEA_WAYBILL,DocumentType.CHARTER_PARTY_BILL_OF_LADING,DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT,DocumentType.AIR_WAYBILL);
 private static final Pattern ON_BOARD=Pattern.compile("(?i)\\bon\\s+board\\b");
 private static final Pattern ORIGINALS=Pattern.compile("(?i)(?:number\\s+of\\s+(?:original\\s+)?(?:bills?\\s+of\\s+lading|b/?l|originals?)|no\\.?\\s+of\\s+originals?)\\s{0,3}[:\\-]?\\s{0,3}(?:(one|two|three|four|[1-4])\\b)");
 private static final Pattern ORIGINALS_WORD=Pattern.compile("(?i)\\b(one|two|three|four)\\s{0,2}(?:\\(\\s?[1-4]\\s?\\)\\s{0,2})?(?:\\(?original|originals\\b)");
 private final SignatureEvidenceService signatures;
 public DocumentFactSuggester(SignatureEvidenceService s){signatures=s;}

 public List<Suggestion> suggest(LcDocument doc){
  var current=RuleFacts.read(doc.getRuleFactsJson());
  var out=new ArrayList<Suggestion>();
  String text=doc.getExtractedText()==null?"":doc.getExtractedText();
  boolean transport=TRANSPORT.contains(doc.getDocumentType());
  var evidence=signatures.evidence(doc);
  if(evidence.available()&&evidence.anyInk())add(out,current,Field.DOCUMENT_SIGNED,"true","Handschriftliche Tinte an der Unterschriftszeile erkannt");
  if(transport){
   var date=DocumentDateDetector.detect(text);
   if(ON_BOARD.matcher(text).find()&&date.date()!=null&&"DETECTED".equals(date.status())){
    add(out,current,Field.DOCUMENT_ON_BOARD_DATE,date.date().toString(),"On-Board-Datum im Text erkannt");
    add(out,current,Field.DOCUMENT_ON_BOARD_NOTATION_PRESENT,"true","On-Board-Vermerk im Text erkannt");
   }
   var m=ORIGINALS.matcher(text);String count=m.find()?number(m.group(1)):null;
   if(count==null){var w=ORIGINALS_WORD.matcher(text);if(w.find())count=number(w.group(1));}
   if(count!=null)add(out,current,Field.DOCUMENT_ORIGINAL_COUNT,count,"Anzahl Originale im Text erkannt");
  }
  if(transport){
   labelled(text,"port\\s+of\\s+loading").ifPresent(v->add(out,current,Field.DOCUMENT_LOADING_PORT,v,"Verladehafen im Text erkannt"));
   labelled(text,"port\\s+of\\s+discharge").ifPresent(v->add(out,current,Field.DOCUMENT_DISCHARGE_PORT,v,"Entladehafen im Text erkannt"));
   labelled(text,"carrier").ifPresent(v->add(out,current,Field.DOCUMENT_CARRIER,v,"Frachtführer im Text erkannt"));
  }
  if(doc.getDocumentType()==DocumentType.PACKING_LIST){
   var pl=PackingListFacts.detect(text);
   if(pl.packageCount()!=null)add(out,current,Field.DOCUMENT_PACKAGE_COUNT,String.valueOf(pl.packageCount()),"Packstückzahl im Text erkannt ("+pl.packageUnit()+")");
   if(pl.grossWeight()!=null)add(out,current,Field.DOCUMENT_GROSS_WEIGHT,pl.grossWeight(),"Bruttogewicht im Text erkannt");
   if(pl.netWeight()!=null)add(out,current,Field.DOCUMENT_NET_WEIGHT,pl.netWeight(),"Nettogewicht im Text erkannt");
   if(pl.weightUnit()!=null)add(out,current,Field.DOCUMENT_WEIGHT_UNIT,pl.weightUnit(),"Gewichtseinheit im Text erkannt");
  }else{
   weight(text,"(?:total\\s+)?gross\\s+weight|gross\\s+wt\\.?|brutto(?:gewicht)?").ifPresent(w->{
    add(out,current,Field.DOCUMENT_GROSS_WEIGHT,w[0],"Bruttogewicht im Text erkannt");
    if(w[1]!=null)add(out,current,Field.DOCUMENT_WEIGHT_UNIT,w[1],"Gewichtseinheit im Text erkannt");
   });
   weight(text,"(?:total\\s+)?net\\s+weight|net\\s+wt\\.?|netto(?:gewicht)?").ifPresent(w->{
    add(out,current,Field.DOCUMENT_NET_WEIGHT,w[0],"Nettogewicht im Text erkannt");
    if(w[1]!=null&&out.stream().noneMatch(x->x.field()==Field.DOCUMENT_WEIGHT_UNIT))add(out,current,Field.DOCUMENT_WEIGHT_UNIT,w[1],"Gewichtseinheit im Text erkannt");
   });
  }
  var parties=DocumentPartyFacts.detect(text);
  if(parties.applicantCountry()!=null)add(out,current,Field.DOCUMENT_APPLICANT_ADDRESS_COUNTRY,parties.applicantCountry(),"Land der Auftraggeber-Adresse im Text erkannt: "+parties.applicantSource());
  if(parties.beneficiaryCountry()!=null)add(out,current,Field.DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY,parties.beneficiaryCountry(),"Land der Begünstigten-Adresse im Text erkannt: "+parties.beneficiarySource());
  return out;
 }
 /** Value after a printed label on the same line, or on the next non-empty line; null unless exactly one distinct plausible value exists. */
 static Optional<String> labelled(String text,String label){
  var matcher=Pattern.compile("(?im)^[\\t ]*"+label+"\\b[^\\n:]{0,25}?[\\t ]*[:\\-]?[\\t ]*([^\\n]*)(?=(?:\\n([^\\n]*))?)").matcher(text);
  var values=new LinkedHashSet<String>();
  while(matcher.find()){
   String value=clean(matcher.group(1));
   if(value.isEmpty()&&matcher.group(2)!=null)value=clean(matcher.group(2));
   if(!value.isEmpty())values.add(value);
  }
  return values.size()==1?Optional.of(values.iterator().next()):Optional.empty();
 }
 private static String clean(String raw){
  String v=raw==null?"":raw.trim().replaceAll("\\s{2,}"," ");
  if(v.length()<2||v.length()>60||!v.matches("[\\p{L}][\\p{L} .,'/()\\-]*"))return "";
  if(v.matches("(?i).*\\b(port of|place of|vessel|voyage|carrier|notify|consignee|weight|date)\\b.*"))return "";
  return v;
 }
 /** {number, unit or null}; null unless the label occurs with one consistent value. */
 static Optional<String[]> weight(String text,String label){
  var matcher=Pattern.compile("(?i)(?:"+label+")\\b[^\\d\\n]{0,20}(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,3})?|\\d+)\\s{0,2}(kgs?|kilos?|mt|tons?|tonnes?|t|lbs?)?\\b").matcher(text);
  var found=new LinkedHashSet<String>();String unit=null;
  while(matcher.find()){
   String n=normalizeNumber(matcher.group(1));if(n==null)continue;
   found.add(n);String u=unit(matcher.group(2));if(u!=null)unit=u;
  }
  return found.size()==1?Optional.of(new String[]{found.iterator().next(),unit}):Optional.empty();
 }
 static String unit(String raw){
  if(raw==null)return null;
  return switch(raw.toLowerCase(Locale.ROOT)){case "kg","kgs","kilo","kilos"->"KG";case "mt","t","ton","tons","tonne","tonnes"->"T";case "lb","lbs"->"LB";default->null;};
 }
 static String normalizeNumber(String raw){
  String n=raw.trim();
  int dot=n.lastIndexOf('.'),comma=n.lastIndexOf(',');
  if(dot>=0&&comma>=0)n=dot>comma?n.replace(",",""):n.replace(".","").replace(',','.');
  else if(comma>=0)n=n.length()-comma==4&&n.indexOf(',')==comma?n.replace(",",""):n.replace(',','.');
  else if(dot>=0&&n.length()-dot==4&&n.indexOf('.')==dot)n=n.replace(".","");
  return n.matches("\\d+(\\.\\d+)?")?n:null;
 }
 static String number(String s){
  return switch(s.toLowerCase(Locale.ROOT)){case "one"->"1";case "two"->"2";case "three"->"3";case "four"->"4";default->s;};
 }
 private static void add(List<Suggestion> out,Map<Field,String> current,Field f,String value,String source){
  String existing=current.get(f);
  if(value.equals(existing))return;
  out.add(new Suggestion(f,value,source,existing));
 }
}
