package de.ostms.lc.rulepack;
import de.ostms.lc.document.domain.DocumentType;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** Labelled values from the document text: quantities, prices, numbers, parties, insurance and draft terms. A value is proposed only if the label occurs with exactly one distinct value. */
public final class DocumentTextFacts {
 public record Fact(Field field,String value,String source){}
 private static final String UNITS="PCS|PC|PIECES|SETS|UNITS|KGS?|MT|TONS?|TONNES|CARTONS|CTNS|PKGS|BAGS|DRUMS|LTRS?|M2|M3";
 private static final Pattern QUANTITY=Pattern.compile("(?i)\\bquantity\\b[^\\d\\n]{0,15}(\\d[\\d.,]*)\\s{0,2}("+UNITS+")?\\b");
 private static final Pattern UNIT_PRICE=Pattern.compile("(?i)unit\\s+price\\b[^\\dA-Za-z\\n]{0,15}(?:[A-Z]{3}\\s{0,2})?(\\d[\\d.,]*\\d)");
 private static final Pattern NUMBER=Pattern.compile("(?i)\\b(?:invoice|b/?l|bill\\s+of\\s+lading|awb|air\\s+waybill|certificate|policy)\\s{0,2}(?:no\\.?|number|nr\\.?)\\s{0,2}[:\\-]?\\s{0,2}([A-Z0-9][A-Z0-9/\\-.]{3,29})");
 private static final Pattern INVOICE_REF=Pattern.compile("(?i)\\binvoice\\s{0,2}(?:no\\.?|number|nr\\.?)\\s{0,2}[:\\-]?\\s{0,2}([A-Z0-9][A-Z0-9/\\-.]{3,29})");
 private static final String DATE="(\\d{1,2}[./]\\d{1,2}[./]\\d{4}|\\d{4}-\\d{2}-\\d{2})";
 private static final Pattern TENOR=Pattern.compile("(?i)\\bat\\s+(\\d{1,3})\\s+days?\\s+(?:after|from)\\s+([A-Za-z/ ]{3,30}?)\\s*(?:[.,\\n]|$)");
 private DocumentTextFacts(){}

 public static List<Fact> detect(DocumentType type,String text){
  var out=new ArrayList<Fact>();if(text==null||text.isBlank())return out;
  var quantity=single(text,QUANTITY,1).map(DocumentFactSuggester::normalizeNumber);
  if(quantity.isPresent()){
   out.add(new Fact(Field.DOCUMENT_QUANTITY,quantity.get(),"Menge im Text erkannt"));
   unit(text).ifPresent(u->out.add(new Fact(Field.DOCUMENT_QUANTITY_UNIT,u,"Mengeneinheit im Text erkannt")));
  }
  single(text,UNIT_PRICE,1).ifPresent(p->{String n=DocumentFactSuggester.normalizeNumber(p);if(n!=null)out.add(new Fact(Field.DOCUMENT_UNIT_PRICE_AMOUNT,n,"Einzelpreis im Text erkannt"));});
  single(text,NUMBER,1).ifPresent(v->out.add(new Fact(Field.DOCUMENT_NUMBER,v,"Dokumentnummer im Text erkannt")));
  if(type!=DocumentType.COMMERCIAL_INVOICE)single(text,INVOICE_REF,1).ifPresent(v->out.add(new Fact(Field.DOCUMENT_INVOICE_REFERENCE,v,"Rechnungsreferenz im Text erkannt")));
  labelledDate(text,"(?:date\\s+of\\s+shipment|shipment\\s+date|shipped\\s+on)").ifPresent(d->out.add(new Fact(Field.DOCUMENT_SHIPMENT_DATE,d,"Versanddatum im Text erkannt")));
  if(type==DocumentType.BILL_OF_LADING||type==DocumentType.SEA_WAYBILL||type==DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT||type==DocumentType.CHARTER_PARTY_BILL_OF_LADING||type==DocumentType.AIR_WAYBILL){
   DocumentFactSuggester.labelled(text,"consignee").ifPresent(v->out.add(new Fact(Field.DOCUMENT_CONSIGNEE,v,"Konsignatar im Text erkannt")));
   DocumentFactSuggester.labelled(text,"notify(?:\\s+party)?").ifPresent(v->out.add(new Fact(Field.DOCUMENT_NOTIFY_PARTY,v,"Notify-Partei im Text erkannt")));
  }
  if(type==DocumentType.INSURANCE_CERTIFICATE){
   DocumentFactSuggester.labelled(text,"insured(?:\\s+party)?").ifPresent(v->out.add(new Fact(Field.DOCUMENT_INSURED_PARTY,v,"Versicherter im Text erkannt")));
   labelledDate(text,"(?:effective\\s+date|date\\s+of\\s+issue|cover(?:age)?\\s+(?:date|from|commences))").ifPresent(d->out.add(new Fact(Field.DOCUMENT_INSURANCE_EFFECTIVE_DATE,d,"Beginn der Deckung im Text erkannt")));
   var range=Pattern.compile("(?i)\\bfrom\\s*[:\\-]?\\s*"+DATE+"\\s+(?:to|until|till)\\s*[:\\-]?\\s*"+DATE).matcher(text);
   var pairs=new LinkedHashSet<String>();while(range.find()){String a=iso(range.group(1)),b=iso(range.group(2));if(a!=null&&b!=null)pairs.add(a+"|"+b);}
   if(pairs.size()==1){String[] p=pairs.iterator().next().split("\\|");out.add(new Fact(Field.DOCUMENT_COVERAGE_FROM,p[0],"Deckungszeitraum im Text erkannt"));out.add(new Fact(Field.DOCUMENT_COVERAGE_TO,p[1],"Deckungszeitraum im Text erkannt"));}
   if(Pattern.compile("(?i)\\bpolicy\\b").matcher(text).find()&&!Pattern.compile("(?i)\\bcertificate\\b").matcher(text).find())out.add(new Fact(Field.DOCUMENT_INSURANCE_TYPE,"POLICY","Versicherungspolice im Text erkannt"));
   else if(Pattern.compile("(?i)insurance\\s+certificate").matcher(text).find())out.add(new Fact(Field.DOCUMENT_INSURANCE_TYPE,"CERTIFICATE","Versicherungszertifikat im Text erkannt"));
  }
  if(type==DocumentType.BILL_OF_EXCHANGE){
   DocumentFactSuggester.labelled(text,"(?:drawn\\s+on|drawee)").ifPresent(v->out.add(new Fact(Field.DOCUMENT_DRAWEE,v,"Bezogener im Text erkannt")));
   if(Pattern.compile("(?i)\\bat\\s+sight\\b").matcher(text).find()){out.add(new Fact(Field.DOCUMENT_DRAFT_TENOR_DAYS,"0","Sichttratte im Text erkannt"));out.add(new Fact(Field.DOCUMENT_DRAFT_TENOR_BASIS,"SIGHT","Sichttratte im Text erkannt"));}
   else{var m=TENOR.matcher(text);var found=new LinkedHashSet<String>();while(m.find())found.add(Integer.parseInt(m.group(1))+"|"+m.group(2).trim().toUpperCase(Locale.ROOT));
    if(found.size()==1){String[] p=found.iterator().next().split("\\|");out.add(new Fact(Field.DOCUMENT_DRAFT_TENOR_DAYS,p[0],"Laufzeit im Text erkannt"));out.add(new Fact(Field.DOCUMENT_DRAFT_TENOR_BASIS,p[1],"Laufzeit im Text erkannt"));}}
  }
  return out;
 }

 /** The only match of the pattern's group, or empty if it is absent or ambiguous. */
 private static Optional<String> single(String text,Pattern p,int group){
  var m=p.matcher(text);var values=new LinkedHashSet<String>();
  while(m.find())if(m.group(group)!=null)values.add(m.group(group).trim());
  return values.size()==1?Optional.of(values.iterator().next()):Optional.empty();
 }
 private static Optional<String> unit(String text){
  var m=QUANTITY.matcher(text);var units=new LinkedHashSet<String>();
  while(m.find())if(m.group(2)!=null)units.add(m.group(2).toUpperCase(Locale.ROOT));
  return units.size()==1?Optional.of(units.iterator().next()):Optional.empty();
 }
 private static Optional<String> labelledDate(String text,String label){
  var m=Pattern.compile("(?i)"+label+"\\b[^\\d\\n]{0,15}"+DATE).matcher(text);var dates=new LinkedHashSet<String>();
  while(m.find()){String d=iso(m.group(1));if(d!=null)dates.add(d);}
  return dates.size()==1?Optional.of(dates.iterator().next()):Optional.empty();
 }
 static String iso(String raw){
  try{
   if(raw.matches("\\d{4}-\\d{2}-\\d{2}"))return LocalDate.parse(raw).toString();
   String[] p=raw.split("[./]");return LocalDate.of(Integer.parseInt(p[2]),Integer.parseInt(p[1]),Integer.parseInt(p[0])).toString();
  }catch(Exception invalid){return null;}
 }
}
