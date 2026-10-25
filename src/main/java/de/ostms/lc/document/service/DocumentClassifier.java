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
  HEADINGS.put(DocumentType.INSURANCE_CERTIFICATE,"(?:certificate[ ]*\\([ ]*policy[ ]*\\)[ ]*of[ ]*(?:marine[ ]*)?insurance|certificate[ ]*/[ ]*policy[ ]*of[ ]*(?:marine[ ]*)?insurance|certificate of marine insurance|marine cargo insurance (?:certificate|policy)|marine insurance policy|marine insurance certificate|insurance certificate|certificate of insurance|insurance policy|versicherungszertifikat|versicherungspolice|versicherungsschein)");
  HEADINGS.put(DocumentType.ROAD_CONSIGNMENT_NOTE,"(?:cmr|road consignment note|international consignment note)");
  HEADINGS.put(DocumentType.INSPECTION_CERTIFICATE,"(?:inspection certificate|certificate of inspection|inspektionszertifikat)");
  HEADINGS.put(DocumentType.BILL_OF_EXCHANGE,"(?:bill of exchange|draft|wechsel)");
  HEADINGS.put(DocumentType.BENEFICIARY_CERTIFICATE,"(?:beneficiary.?s certificate|beneficiary certificate|begünstigtenzertifikat)");
  HEADINGS.put(DocumentType.QUALITY_CERTIFICATE,"(?:quality certificate|certificate of quality|certificate of analysis|qualitätszertifikat)");
  HEADINGS.put(DocumentType.DOCUMENT_SUBMISSION,"(?:dokumenteneinreichung|einreichung der dokumente|dokumentenvorlage|document submission|submission of documents|presentation of documents|document presentation|presentation letter)");
  HEADINGS.put(DocumentType.COURIER_RECEIPT,"(?:courier receipt|courier delivery receipt|kurierbeleg)");
 }
 private DocumentClassifier(){}
 public static Classification classify(String filename,String text){return classify(filename,text,Set.of());}
 /** With the document types the LC asks for: they decide between several headings or weak profiles; a type the LC does not ask for is offered for review only. */
 public static Classification classify(String filename,String text,Set<DocumentType> expected){
  var base=classifyWith(filename,text,expected==null?Set.of():expected);
  if(expected==null||expected.isEmpty()||base.suggestedType()==null||expected.contains(base.suggestedType())||base.suggestedType()==DocumentType.SWIFT_MT700||base.suggestedType()==DocumentType.ADVISING_LETTER||base.suggestedType()==DocumentType.DOCUMENT_SUBMISSION)return base;
  var evidence=new ArrayList<>(base.evidence());evidence.add("Dokumentart wird im LC nicht gefordert – bitte prüfen");
  return new Classification(base.suggestedType(),Math.min(base.score(),.75),"REVIEW",base.method(),List.copyOf(evidence));
 }
 private static Classification classifyWith(String filename,String text,Set<DocumentType> expected){
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
  // "Packing list / weight list" is one combined document; a submission letter names the documents it encloses.
  if(Pattern.compile("(?im)^"+decor+"(?:detailed )?packing list[\\t ]*+[/&+-][\\t ]*+weight list"+decor+"(?:(?:original|copy|kopie|duplicate)"+decor+")?$").matcher(headingText).find())return new Classification(DocumentType.PACKING_LIST,.9,"SUGGESTED","DOCUMENT_HEADINGS_V3",List.of("Packing list / weight list"));
  // Bilingual / form-style certificates of origin (EUR.1 and similar): the title is printed as "Certificate of origin - Certificat d'origine".
  if(found.isEmpty()&&Pattern.compile("(?i)certificate of origin[\\t ]*+[-–—][\\t ]*+certificat d.origine|warenverkehrsbescheinigung|consignor[\\t ]*+[-–—][\\t ]*+exp[eé]diteur").matcher(headingText).find())return new Classification(DocumentType.CERTIFICATE_OF_ORIGIN,.9,"SUGGESTED","DOCUMENT_HEADINGS_V3",List.of("Ursprungs-/Warenverkehrsbescheinigung (Formular)"));
  if(found.containsKey(DocumentType.DOCUMENT_SUBMISSION)&&found.size()>1){
   var submission=Pattern.compile("(?im)^"+decor+"(?:dokumenteneinreichung|einreichung der dokumente|dokumentenvorlage|document submission|submission of documents|presentation of documents|document presentation|presentation letter)"+decor+"$").matcher(headingText);
   if(submission.find()&&submission.start()<800)return new Classification(DocumentType.DOCUMENT_SUBMISSION,.9,"SUGGESTED","DOCUMENT_HEADINGS_V3",List.of(found.get(DocumentType.DOCUMENT_SUBMISSION)));
  }
  if(found.size()>1&&pure.size()==1){var only=pure.iterator().next();return new Classification(only,.9,"SUGGESTED","DOCUMENT_HEADINGS_V2",List.of(found.get(only)));}
  if(found.size()>1){
   var wanted=found.keySet().stream().filter(expected::contains).toList();
   if(wanted.size()==1)return new Classification(wanted.get(0),.85,"SUGGESTED","LC_EXPECTED_V1",List.of(found.get(wanted.get(0)),"Mehrere Überschriften; diese Dokumentart fordert das LC"));
   return new Classification(null,0,"AMBIGUOUS","DOCUMENT_HEADINGS_V1",List.copyOf(found.values()));
  }
  if(found.size()==1){var entry=found.entrySet().iterator().next();return new Classification(entry.getKey(),.9,"SUGGESTED","DOCUMENT_HEADINGS_V1",List.of(entry.getValue()));}
  var profile=profile(normalized,expected);
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
  PROFILE_TERMS.put(DocumentType.AIR_WAYBILL,List.of("airport of departure","airport of destination","shipper's account","consignee's account","issuing carrier","iata","flight","chargeable weight"));
  PROFILE_TERMS.put(DocumentType.INSURANCE_CERTIFICATE,List.of("insured","sum insured","policy no","claims payable","institute cargo clauses","underwriter","premium","assured"));
  PROFILE_TERMS.put(DocumentType.CERTIFICATE_OF_ORIGIN,List.of("country of origin","chamber of commerce","exporter","consignor","we hereby certify","origin of the goods","certified","handelskammer"));
  PROFILE_TERMS.put(DocumentType.ROAD_CONSIGNMENT_NOTE,List.of("sender","carrier","place of delivery","place and date of taking over","successive carriers","convention","vehicle","documents attached"));
  PROFILE_TERMS.put(DocumentType.BILL_OF_EXCHANGE,List.of("pay to the order of","at sight","days after","value received","drawee","drawn under","exchange for","the sum of"));
  PROFILE_TERMS.put(DocumentType.INSPECTION_CERTIFICATE,List.of("inspection","inspected","inspector","surveyor","we hereby certify","sampling","found to be","in accordance with"));
 }
 /** Weak, field-based hint for pages without a heading; never reaches the automatic split threshold. */
 private static Classification profile(String text,Set<DocumentType> expected){
  String lower=text.toLowerCase(Locale.ROOT);
  var hits=new LinkedHashMap<DocumentType,List<String>>();
  PROFILE_TERMS.forEach((type,terms)->hits.put(type,terms.stream().filter(lower::contains).toList()));
  int max=hits.values().stream().mapToInt(List::size).max().orElse(0);if(max==0)return null;
  var top=hits.entrySet().stream().filter(e->e.getValue().size()==max).map(Map.Entry::getKey).toList();
  DocumentType best=top.size()==1?top.get(0):null;
  // A tie between profiles is decided by the documents the LC asks for, if exactly one of them is expected.
  if(best==null){var wanted=top.stream().filter(expected::contains).toList();if(wanted.size()==1)best=wanted.get(0);}
  // An expected type needs one typical field less.
  if(best==null||max<(expected.contains(best)?3:4))return null;
  return new Classification(best,.7,"REVIEW","KEYWORD_PROFILE_V1",List.of("Typische Felder erkannt: "+String.join(", ",hits.get(best))+" – bitte prüfen"));
 }

}
