package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The number a document carries itself: invoice number on invoices and the number of a bill of lading, waybill, packing list or delivery note.
 * Only an explicit "No./Nr./Number/#" caption counts and the value must contain a digit; a value on the next line must stand alone there.
 * An invoice number printed on another document type refers to the invoice, so it is not taken for that document.
 */
final class DocumentNumberDetector {
 private DocumentNumberDetector(){}
 private static final String VALUE="([A-Z0-9][A-Z0-9./_-]{2,})";
 private static final String CAPTION="[\\t ]*+(?:no\\.?|nr\\.?|number|nummer|#|-nr\\.?|-nummer)[\\t ]*+[:#-]?[\\t ]*+";
 private static Pattern pattern(String labels){
  return Pattern.compile("(?im)(?<![\\p{L}\\p{N}])(?:"+labels+")"+CAPTION+"(?:"+VALUE+"(?=[\\t ]|$)|\\r?\\n[\\t ]*+"+VALUE+"[\\t ]*+(?:\\r?\\n|$))");
 }
 private static final Pattern INVOICE=pattern("commercial invoice|tax invoice|proforma invoice|final invoice|invoice|rechnung|rechn\\.|rechnungs|beleg");
 private static final Pattern TRANSPORT=pattern("b/?l\\.?|bill of lading|sea waybill|waybill|air waybill|awb|bol|konnossement|frachtbrief|cmr");
 private static final Pattern PACKING=pattern("packing list|packing note|packliste|delivery note|lieferschein|weight list|gewichtsliste");

 static Optional<String> detect(String text,DocumentType type){
  if(text==null)return Optional.empty();
  String head=text.substring(0,Math.min(text.length(),100_000));
  boolean transport=type==DocumentType.BILL_OF_LADING||type==DocumentType.SEA_WAYBILL||type==DocumentType.AIR_WAYBILL||type==DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT||type==DocumentType.CHARTER_PARTY_BILL_OF_LADING||type==DocumentType.ROAD_CONSIGNMENT_NOTE;
  boolean packing=type==DocumentType.PACKING_LIST||type==DocumentType.WEIGHT_LIST;
  if(type==DocumentType.COMMERCIAL_INVOICE)return single(INVOICE,head);
  if(transport)return single(TRANSPORT,head);
  if(packing)return single(PACKING,head);
  // type not known: the document's own caption decides, an invoice number first
  return single(INVOICE,head).or(()->single(TRANSPORT,head)).or(()->single(PACKING,head));
 }
 private static Optional<String> single(Pattern pattern,String text){
  var values=new LinkedHashSet<String>();var m=pattern.matcher(text);
  while(m.find()){String v=m.group(1)!=null?m.group(1):m.group(2);if(v!=null&&v.chars().anyMatch(Character::isDigit))values.add(v);}
  return values.size()==1?Optional.of(values.iterator().next()):Optional.empty();
 }
}
