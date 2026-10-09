package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.DocumentType;
import java.util.*;
import java.util.regex.Pattern;
/** Scores indicate strength of classification evidence, not a calibrated probability. */
public final class DocumentClassifier {
 public record Classification(DocumentType suggestedType,double score,String status,String method,List<String> evidence){}
 private static final Map<DocumentType,String> HEADINGS=new LinkedHashMap<>();
 static{
  HEADINGS.put(DocumentType.COMMERCIAL_INVOICE,"(?:commercial invoice|final invoice|tax invoice|invoice|handelsrechnung|rechnung)");
  HEADINGS.put(DocumentType.PACKING_LIST,"(?:detailed packing list|packing list|packing slip|packing note|packliste)");
  HEADINGS.put(DocumentType.BILL_OF_LADING,"(?:ocean bill of lading|marine bill of lading|master bill of lading|through bill of lading|bill of lading|konnossement)");
  HEADINGS.put(DocumentType.SEA_WAYBILL,"(?:non[- ]negotiable sea waybill|sea waybill|seefrachtbrief)");
  HEADINGS.put(DocumentType.CHARTER_PARTY_BILL_OF_LADING,"(?:charter[ -]?party bill of lading|charterpartie[- ]konnossement)");
  HEADINGS.put(DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT,"(?:multimodal transport document|combined transport document|multimodales transportdokument)");
  HEADINGS.put(DocumentType.WEIGHT_LIST,"(?:weight list|weight certificate|weight note|certificate of weight|gewichtsliste)");
  HEADINGS.put(DocumentType.POST_RECEIPT,"(?:post receipt|postal receipt|posteinlieferungsbeleg)");
  HEADINGS.put(DocumentType.AIR_WAYBILL,"(?:air waybill|airway bill|luftfrachtbrief)");
  HEADINGS.put(DocumentType.CERTIFICATE_OF_ORIGIN,"(?:certificate of non[- ]preferential origin|certificate of origin|ursprungszeugnis)");
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
  String headingText=text==null?"":text.substring(0,Math.min(text.length(),4000)).replaceAll("[\\t ]+"," ");
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
  Map<DocumentType,String> found=new LinkedHashMap<>();Set<DocumentType> pure=new HashSet<>();
  String decor="[\\t \\p{Punct}*–—]*+";
  HEADINGS.forEach((type,pattern)->{
   var heading=Pattern.compile("(?im)^"+decor+"("+pattern+")"+decor+"(?:(?:original|copy|kopie|duplicate)"+decor+")?$").matcher(headingText);
   if(heading.find()){found.put(type,heading.group().trim());pure.add(type);return;}
   var labelled=Pattern.compile("(?im)^[\\t ]*+("+pattern+")[\\t ]*+(?:no\\.?|number|nr\\.?)\\b").matcher(headingText);
   if(labelled.find())found.put(type,labelled.group().trim());
  });
  if(found.size()>1&&pure.size()==1){var only=pure.iterator().next();return new Classification(only,.9,"SUGGESTED","DOCUMENT_HEADINGS_V2",List.of(found.get(only)));}
  if(found.size()>1)return new Classification(null,0,"AMBIGUOUS","DOCUMENT_HEADINGS_V1",List.copyOf(found.values()));
  if(found.size()==1){var entry=found.entrySet().iterator().next();return new Classification(entry.getKey(),.9,"SUGGESTED","DOCUMENT_HEADINGS_V1",List.of(entry.getValue()));}
  var profile=profile(normalized);
  if(profile!=null)return profile;
  String name=filename==null?"":filename.replaceAll("[_./\\\\-]+"," ").toLowerCase(Locale.ROOT);
  if(Pattern.compile("\\bmt[\\t ]*+700\\b").matcher(name).find())return new Classification(DocumentType.SWIFT_MT700,.55,"REVIEW","FILENAME_HINT_V2",List.of(filename));
  if(Pattern.compile("\\b(?:avisierung(?:sschreiben)?|akkreditivavis|advising[\\t ]++letter)\\b").matcher(name).find())return new Classification(DocumentType.ADVISING_LETTER,.55,"REVIEW","FILENAME_HINT_V2",List.of(filename));
  HEADINGS.forEach((type,pattern)->{if(Pattern.compile("\\b"+pattern+"\\b").matcher(name).find())found.put(type,filename);});
  if(found.size()==1)return new Classification(found.keySet().iterator().next(),.55,"REVIEW","FILENAME_HINT_V1",List.of(filename));
  return new Classification(null,0,found.isEmpty()?"UNKNOWN":"AMBIGUOUS","DOCUMENT_HEADINGS_V1",List.copyOf(found.values()));
 }
 private static final Map<DocumentType,List<String>> PROFILE_TERMS=new LinkedHashMap<>();
 static{
  PROFILE_TERMS.put(DocumentType.BILL_OF_LADING,List.of("shipper","consignee","notify party","port of loading","port of discharge","vessel","freight","shipped on board"));
  PROFILE_TERMS.put(DocumentType.PACKING_LIST,List.of("packing","gross weight","net weight","packages","carton","measurement","marks and numbers"));
  PROFILE_TERMS.put(DocumentType.COMMERCIAL_INVOICE,List.of("invoice","unit price","amount due","total amount","terms of payment","incoterms","buyer","seller"));
 }
 /** Weak, field-based hint for pages without a heading; never reaches the automatic split threshold. */
 private static Classification profile(String text){
  String lower=text.toLowerCase(Locale.ROOT);DocumentType best=null;int bestHits=0;boolean tie=false;List<String> bestTerms=List.of();
  for(var entry:PROFILE_TERMS.entrySet()){
   var hits=entry.getValue().stream().filter(lower::contains).toList();
   if(hits.size()>bestHits){best=entry.getKey();bestHits=hits.size();bestTerms=hits;tie=false;}
   else if(hits.size()==bestHits&&bestHits>0)tie=true;
  }
  if(best==null||bestHits<4||tie)return null;
  return new Classification(best,.7,"REVIEW","KEYWORD_PROFILE_V1",List.of("Typische Felder erkannt: "+String.join(", ",bestTerms)+" – bitte prüfen"));
 }
}
