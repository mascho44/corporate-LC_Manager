package de.corporate.lc.document.service;
import de.corporate.lc.document.domain.DocumentType;
import java.util.*;
import java.util.regex.Pattern;
/** Scores indicate strength of classification evidence, not a calibrated probability. */
public final class DocumentClassifier {
 public record Classification(DocumentType suggestedType,double score,String status,String method,List<String> evidence){}
 private static final Map<DocumentType,String> HEADINGS=new LinkedHashMap<>();
 static{
  HEADINGS.put(DocumentType.COMMERCIAL_INVOICE,"(?:commercial invoice|invoice|handelsrechnung)");
  HEADINGS.put(DocumentType.PACKING_LIST,"(?:packing list|packliste)");
  HEADINGS.put(DocumentType.BILL_OF_LADING,"(?:bill of lading|ocean bill of lading|konnossement)");
  HEADINGS.put(DocumentType.AIR_WAYBILL,"(?:air waybill|airway bill|luftfrachtbrief)");
  HEADINGS.put(DocumentType.CERTIFICATE_OF_ORIGIN,"(?:certificate of origin|ursprungszeugnis)");
  HEADINGS.put(DocumentType.INSURANCE_CERTIFICATE,"(?:insurance certificate|certificate of insurance|insurance policy|versicherungszertifikat)");
 }
 private DocumentClassifier(){}
 public static Classification classify(String filename,String text){
  String headingText=text==null?"":text.substring(0,Math.min(text.length(),3000)).replaceAll("[\\t ]+"," ");
  if(Pattern.compile("(?m)^:\\d{2}[A-Z]?:").matcher(headingText).find())return new Classification(null,0,"UNKNOWN","SWIFT_MESSAGE_V1",List.of("SWIFT-Felder erkannt – kein Handelsdokumenttyp vorgeschlagen"));
  Map<DocumentType,String> found=new LinkedHashMap<>();
  HEADINGS.forEach((type,pattern)->{var matcher=Pattern.compile("(?im)^\\s*("+pattern+")(?:\\s*$|\\s+(?:no\\.?|number|nr\\.?)\\b)").matcher(headingText);if(matcher.find())found.put(type,matcher.group().trim());});
  if(found.size()>1)return new Classification(null,0,"AMBIGUOUS","DOCUMENT_HEADINGS_V1",List.copyOf(found.values()));
  if(found.size()==1){var entry=found.entrySet().iterator().next();return new Classification(entry.getKey(),.9,"SUGGESTED","DOCUMENT_HEADINGS_V1",List.of(entry.getValue()));}
  String name=filename==null?"":filename.replaceAll("[_./\\\\-]+"," ").toLowerCase(Locale.ROOT);
  HEADINGS.forEach((type,pattern)->{if(Pattern.compile("\\b"+pattern+"\\b").matcher(name).find())found.put(type,filename);});
  if(found.size()==1)return new Classification(found.keySet().iterator().next(),.55,"REVIEW","FILENAME_HINT_V1",List.of(filename));
  return new Classification(null,0,found.isEmpty()?"UNKNOWN":"AMBIGUOUS","DOCUMENT_HEADINGS_V1",List.copyOf(found.values()));
 }
}
