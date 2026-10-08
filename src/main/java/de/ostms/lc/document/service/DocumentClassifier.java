package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.DocumentType;
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
  HEADINGS.put(DocumentType.ROAD_CONSIGNMENT_NOTE,"(?:cmr|road consignment note|international consignment note)");
  HEADINGS.put(DocumentType.INSPECTION_CERTIFICATE,"(?:inspection certificate|certificate of inspection|inspektionszertifikat)");
  HEADINGS.put(DocumentType.BILL_OF_EXCHANGE,"(?:bill of exchange|draft|wechsel)");
  HEADINGS.put(DocumentType.BENEFICIARY_CERTIFICATE,"(?:beneficiary.?s certificate|beneficiary certificate|begünstigtenzertifikat)");
  HEADINGS.put(DocumentType.QUALITY_CERTIFICATE,"(?:quality certificate|certificate of quality|certificate of analysis|qualitätszertifikat)");
  HEADINGS.put(DocumentType.COURIER_RECEIPT,"(?:courier receipt|courier delivery receipt|kurierbeleg)");
 }
 private DocumentClassifier(){}
 public static Classification classify(String filename,String text){
  String headingText=text==null?"":text.substring(0,Math.min(text.length(),3000)).replaceAll("[\\t ]+"," ");
  String normalized=headingText.replace('\u00a0',' ').replace('\r','\n');
  var advice=Pattern.compile("(?im)^[\\t ]*+(?:betreff[\\t ]*+:[\\t ]*+)?(?:avisierung(?:sschreiben)?(?:[\\t ]++(?:eines?|des|zum|von|einer))?[\\t ]*+(?:dokumenten[- ]?)?akkreditiv\\w*|akkreditiv[- ]?avis(?:ierung)?|advice[\\t ]++of[\\t ]++(?:a[\\t ]++)?(?:documentary|letter[\\t ]++of)[\\t ]++credit|notification[\\t ]++of[\\t ]++(?:a[\\t ]++)?documentary[\\t ]++credit|avisierungsschreiben)\\b").matcher(normalized);
  var firstTag=Pattern.compile("(?m)^[\\t ]*+:?[\\t ]*+(?:20|27|40A|31D|32B|50|59|46A)[\\t ]*+:").matcher(normalized);
  boolean hasTag=firstTag.find();
  if(advice.find()&&(!hasTag||advice.start()<firstTag.start()))return new Classification(DocumentType.ADVISING_LETTER,.9,"SUGGESTED","LC_CORRESPONDENCE_V2",List.of(advice.group().trim()));
  Set<String> tags=new HashSet<>();var tag=Pattern.compile("(?im)^[\\t ]*+:?[\\t ]*+(20|27|40A|31D|32B|50|59|46A)[\\t ]*+:").matcher(normalized);while(tag.find())tags.add(tag.group(1).toUpperCase(Locale.ROOT));
  boolean mt700Heading=Pattern.compile("(?i)\\bMT[\\t ]*+[-:]?[\\t ]*+700\\b").matcher(normalized).find();
  if((tags.contains("40A")&&tags.contains("20")&&tags.size()>=3)||(mt700Heading&&tags.size()>=2))return new Classification(DocumentType.SWIFT_MT700,.95,"SUGGESTED","SWIFT_MT700_V2",List.of("MT700-Struktur erkannt: "+String.join(", ",new TreeSet<>(tags))));
  if(mt700Heading)return new Classification(DocumentType.SWIFT_MT700,.75,"REVIEW","SWIFT_MT700_V2",List.of("MT700-Bezeichnung erkannt; Feldstruktur bitte prüfen"));
  if(Pattern.compile("(?m)^[\\t ]*+:\\d{2}[A-Z]?:").matcher(normalized).find())return new Classification(null,0,"UNKNOWN","SWIFT_MESSAGE_V2",List.of("SWIFT-Felder erkannt – Format bitte im SWIFT-Training prüfen"));
  Map<DocumentType,String> found=new LinkedHashMap<>();
  HEADINGS.forEach((type,pattern)->{var matcher=Pattern.compile("(?im)^[\\t ]*+("+pattern+")(?:[\\t ]*+$|[\\t ]++(?:no\\.?|number|nr\\.?)\\b)").matcher(headingText);if(matcher.find())found.put(type,matcher.group().trim());});
  if(found.size()>1)return new Classification(null,0,"AMBIGUOUS","DOCUMENT_HEADINGS_V1",List.copyOf(found.values()));
  if(found.size()==1){var entry=found.entrySet().iterator().next();return new Classification(entry.getKey(),.9,"SUGGESTED","DOCUMENT_HEADINGS_V1",List.of(entry.getValue()));}
  String name=filename==null?"":filename.replaceAll("[_./\\\\-]+"," ").toLowerCase(Locale.ROOT);
  if(Pattern.compile("\\bmt[\\t ]*+700\\b").matcher(name).find())return new Classification(DocumentType.SWIFT_MT700,.55,"REVIEW","FILENAME_HINT_V2",List.of(filename));
  if(Pattern.compile("\\b(?:avisierung(?:sschreiben)?|akkreditivavis|advising[\\t ]++letter)\\b").matcher(name).find())return new Classification(DocumentType.ADVISING_LETTER,.55,"REVIEW","FILENAME_HINT_V2",List.of(filename));
  HEADINGS.forEach((type,pattern)->{if(Pattern.compile("\\b"+pattern+"\\b").matcher(name).find())found.put(type,filename);});
  if(found.size()==1)return new Classification(found.keySet().iterator().next(),.55,"REVIEW","FILENAME_HINT_V1",List.of(filename));
  return new Classification(null,0,found.isEmpty()?"UNKNOWN":"AMBIGUOUS","DOCUMENT_HEADINGS_V1",List.copyOf(found.values()));
 }
}
