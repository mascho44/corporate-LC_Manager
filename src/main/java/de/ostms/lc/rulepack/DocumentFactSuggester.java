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
  return out;
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
