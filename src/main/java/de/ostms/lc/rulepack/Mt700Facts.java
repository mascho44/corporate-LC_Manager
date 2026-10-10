package de.ostms.lc.rulepack;
import de.ostms.lc.lc.domain.LetterOfCredit;
import java.util.*;
import java.util.regex.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** Reads rule facts from the structured fields of the MT700 (tags 39A, 40E, 42A/C, 43P/T, 44A–F, 45A, 46A, 47A, 48). Each fact carries the tag it came from. */
public final class Mt700Facts {
 public record Fact(Field field,String value,String source){}
 private static final Pattern INCOTERM=Pattern.compile("\\b(EXW|FCA|FAS|FOB|CFR|CIF|CPT|CIP|DAP|DPU|DDP)\\b");
 private static final Pattern INSURANCE_PERCENT=Pattern.compile("(?i)\\b(\\d{2,3})\\s*(?:PCT|PERCENT|%)\\s+(?:OF\\s+)?(?:THE\\s+)?(?:INVOICE|CIF|CIP|COMMERCIAL)");
 private static final Pattern RISKS=Pattern.compile("(?i)(INSTITUTE\\s+CARGO\\s+CLAUSES?\\s*\\(?\\s*[ABC]\\s*\\)?|\\bICC\\s*\\(?\\s*[ABC]\\s*\\)?|ALL\\s+RISKS|INSTITUTE\\s+WAR\\s+CLAUSES|INSTITUTE\\s+STRIKES\\s+CLAUSES|\\bWAR\\s+RISKS?|\\bSTRIKES?\\s+RISKS?)");
 private static final Pattern DAYS_AFTER=Pattern.compile("(?i)(\\d{1,3})\\s*DAYS?\\s+(?:AFTER|FROM)\\s+(.+)");
 private Mt700Facts(){}

 public static List<Fact> detect(LetterOfCredit lc){
  var tags=tags(lc);var out=new ArrayList<Fact>();
  allowed(out,tags,"43P",Field.LC_PARTIAL_SHIPMENT_ALLOWED);
  allowed(out,tags,"43T",Field.LC_TRANSSHIPMENT_ALLOWED);
  place(out,tags,"44A",Field.LC_PLACE_OF_RECEIPT);place(out,tags,"44E",Field.LC_LOADING_PORT);
  place(out,tags,"44F",Field.LC_DISCHARGE_PORT);place(out,tags,"44B",Field.LC_PLACE_OF_FINAL_DESTINATION);
  String t48=tags.get("48");
  if(t48!=null){var m=Pattern.compile("(?i)\\b(\\d{1,3})\\s*DAYS?\\b").matcher(t48);if(m.find())out.add(new Fact(Field.LC_PRESENTATION_PERIOD_DAYS,m.group(1),"Feld :48:"));}
  String t39a=tags.get("39A");
  if(t39a!=null){
   var m=Pattern.compile("^\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*$").matcher(t39a.trim());
   if(m.matches()&&m.group(1).equals(m.group(2))){out.add(new Fact(Field.LC_TOLERANCE_PERCENT,String.valueOf(Integer.parseInt(m.group(1))),"Feld :39A:"));out.add(new Fact(Field.LC_AMOUNT_TOLERANCE_ALLOWED,"true","Feld :39A:"));}
   else if(m.matches()){out.add(new Fact(Field.LC_AMOUNT_TOLERANCE_ALLOWED,"true","Feld :39A: (ungleiche Toleranzen "+t39a.trim()+" – bitte Prozentwert prüfen)"));}
  }
  String t39b=tags.get("39B");
  if(t39b!=null&&t39b.toUpperCase(Locale.ROOT).contains("NOT EXCEEDING"))out.add(new Fact(Field.LC_AMOUNT_TOLERANCE_ALLOWED,"false","Feld :39B: (Betrag darf nicht überschritten werden)"));
  String t40e=tags.get("40E");
  if(t40e!=null){
   String u=t40e.toUpperCase(Locale.ROOT);
   if(u.contains("UCP"))out.add(new Fact(Field.LC_RULE_STANDARD,"UCP600","Feld :40E:"));
   else if(u.contains("ISP")||u.contains("URDG")||u.contains("OTHR")||u.contains("EUCP"))out.add(new Fact(Field.LC_RULE_STANDARD,"OTHER","Feld :40E:"));
  }
  String t42a=tags.get("42A");
  if(t42a!=null&&!t42a.isBlank())out.add(new Fact(Field.LC_DRAWEE,firstLine(t42a),"Feld :42A:"));
  tenor(out,tags.get("42C"));
  String goods=tags.get("45A");
  if(goods!=null&&!goods.isBlank()){
   out.add(new Fact(Field.LC_GOODS_DESCRIPTION,goods.trim().replaceAll("\\s*\\r?\\n\\s*"," "),"Feld :45A:"));
   var found=new LinkedHashSet<String>();var m=INCOTERM.matcher(goods.toUpperCase(Locale.ROOT));while(m.find())found.add(m.group(1));
   if(found.size()==1){out.add(new Fact(Field.LC_INCOTERM,found.iterator().next(),"Feld :45A:"));out.add(new Fact(Field.LC_INCOTERM_SOURCE,"Feld 45A","Feld :45A:"));}
  }
  if(goods!=null){
   var q=Pattern.compile("(?i)\\b(\\d[\\d.,]*)\\s{0,2}(PCS|PIECES|SETS|UNITS|KGS?|MT|TONS?|CARTONS|CTNS)\\b").matcher(goods);var nums=new LinkedHashSet<String>();var units=new LinkedHashSet<String>();
   while(q.find()){String n=DocumentFactSuggester.normalizeNumber(q.group(1));if(n!=null){nums.add(n);units.add(q.group(2).toUpperCase(Locale.ROOT));}}
   if(nums.size()==1&&units.size()==1){out.add(new Fact(Field.LC_QUANTITY,nums.iterator().next(),"Feld :45A: (Menge)"));out.add(new Fact(Field.LC_QUANTITY_UNIT,units.iterator().next(),"Feld :45A: (Menge)"));}
  }
  String docs=String.join("\n",Objects.toString(tags.get("46A"),""),Objects.toString(tags.get("47A"),""));
  if(!docs.isBlank()){
   String upper=docs.toUpperCase(Locale.ROOT);
   var pct=INSURANCE_PERCENT.matcher(docs);var pcts=new LinkedHashSet<String>();while(pct.find())pcts.add(pct.group(1));
   if(pcts.size()==1)out.add(new Fact(Field.LC_INSURANCE_MIN_PERCENT,pcts.iterator().next(),"Feld :46A: (Versicherung)"));
   var risks=new LinkedHashSet<String>();var r=RISKS.matcher(docs);while(r.find())risks.add(r.group(1).replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT));
   if(!risks.isEmpty())out.add(new Fact(Field.LC_INSURANCE_RISKS,String.join("; ",risks),"Feld :46A: (Versicherung)"));
   if(upper.contains("FREIGHT PREPAID")||upper.contains("FREIGHT PAID")){out.add(new Fact(Field.LC_FREIGHT_PREPAID_REQUIRED,"true","Feld :46A:"));out.add(new Fact(Field.LC_FREIGHT_TERMS,"PREPAID","Feld :46A:"));}
   else if(upper.contains("FREIGHT COLLECT")||upper.contains("FREIGHT PAYABLE AT DESTINATION")){out.add(new Fact(Field.LC_FREIGHT_PREPAID_REQUIRED,"false","Feld :46A:"));out.add(new Fact(Field.LC_FREIGHT_TERMS,"COLLECT","Feld :46A:"));}
   if(Pattern.compile("\\bSHIPPED\\s+ON\\s+BOARD\\b|\\bON\\s+BOARD\\s+NOTATION\\b").matcher(upper).find())out.add(new Fact(Field.LC_ON_BOARD_NOTATION_REQUIRED,"true","Feld :46A:"));
   if(upper.contains("INSURANCE POLICY")&&!upper.contains("INSURANCE CERTIFICATE"))out.add(new Fact(Field.LC_INSURANCE_TYPE,"POLICY","Feld :46A: (Versicherung)"));
   else if(upper.contains("INSURANCE CERTIFICATE")&&!upper.contains("INSURANCE POLICY"))out.add(new Fact(Field.LC_INSURANCE_TYPE,"CERTIFICATE","Feld :46A: (Versicherung)"));
   if(upper.contains("PREMIUM PAID"))out.add(new Fact(Field.LC_PREMIUM_PAID_REQUIRED,"true","Feld :46A:"));
   if(Pattern.compile("\\bENDORSED\\b").matcher(upper).find())out.add(new Fact(Field.LC_ENDORSEMENT_REQUIRED,"true","Feld :46A:"));
   if(Pattern.compile("PRE-?SHIPMENT\\s+INSPECTION").matcher(upper).find())out.add(new Fact(Field.LC_PRESHIPMENT_INSPECTION_REQUIRED,"true","Feld :46A:"));
   if(upper.contains("IRRESPECTIVE OF PERCENTAGE"))out.add(new Fact(Field.LC_IRRESPECTIVE_OF_PERCENTAGE_REQUIRED,"true","Feld :46A:"));
   if(Pattern.compile("NOTIFY\\s+(?:PARTY\\s*:?\\s*)?APPLICANT").matcher(upper).find())out.add(new Fact(Field.LC_NOTIFY_PARTY,"APPLICANT","Feld :46A:"));
   if(Pattern.compile("CONSIGNED\\s+TO\\s+(?:THE\\s+)?ORDER").matcher(upper).find())out.add(new Fact(Field.LC_CONSIGNEE,"TO ORDER","Feld :46A:"));
   var form=Pattern.compile("\\b(FORM\\s+A|EUR\\.?\\s*1|FORM\\s+E)\\b").matcher(upper);var forms=new LinkedHashSet<String>();while(form.find())forms.add(form.group(1).replaceAll("\\s+"," ").replace("EUR. 1","EUR.1").replace("EUR 1","EUR.1"));
   if(forms.size()==1)out.add(new Fact(Field.LC_REQUIRED_FORM_TYPE,forms.iterator().next(),"Feld :46A:"));
   boolean air=upper.contains("AIR WAYBILL")||upper.contains("AIRWAY BILL"),sea=upper.contains("BILL OF LADING")||upper.contains("B/L")||upper.contains("SEA WAYBILL");
   if(air&&!sea){
    var a=tags.get("44E");var b=tags.get("44F");
    if(a!=null)out.add(new Fact(Field.LC_DEPARTURE_AIRPORT,firstLine(a),"Feld :44E: (Luftfracht gefordert)"));
    if(b!=null)out.add(new Fact(Field.LC_DESTINATION_AIRPORT,firstLine(b),"Feld :44F: (Luftfracht gefordert)"));
   }
  }
  return out;
 }

 private static void allowed(List<Fact> out,Map<String,String> tags,String tag,Field field){
  String v=tags.get(tag);if(v==null)return;String u=v.toUpperCase(Locale.ROOT);
  if(u.contains("NOT ALLOWED")||u.contains("PROHIBITED")||u.contains("NOT PERMITTED"))out.add(new Fact(field,"false","Feld :"+tag+":"));
  else if(u.contains("ALLOWED")||u.contains("PERMITTED"))out.add(new Fact(field,"true","Feld :"+tag+":"));
  else if(u.contains("CONDITIONAL"))return;
 }
 private static void place(List<Fact> out,Map<String,String> tags,String tag,Field field){
  String v=tags.get(tag);if(v!=null&&!v.isBlank()){String line=firstLine(v);if(line.length()<=100)out.add(new Fact(field,line,"Feld :"+tag+":"));}
 }
 private static void tenor(List<Fact> out,String t42c){
  if(t42c==null||t42c.isBlank())return;
  String line=t42c.trim().replaceAll("\\s*\\r?\\n\\s*"," ");
  if(line.toUpperCase(Locale.ROOT).contains("SIGHT")){out.add(new Fact(Field.LC_DRAFT_TENOR_DAYS,"0","Feld :42C:"));out.add(new Fact(Field.LC_DRAFT_TENOR_BASIS,"SIGHT","Feld :42C:"));return;}
  var m=DAYS_AFTER.matcher(line);
  if(m.find()){out.add(new Fact(Field.LC_DRAFT_TENOR_DAYS,String.valueOf(Integer.parseInt(m.group(1))),"Feld :42C:"));out.add(new Fact(Field.LC_DRAFT_TENOR_BASIS,m.group(2).trim().toUpperCase(Locale.ROOT),"Feld :42C:"));}
 }
 private static String firstLine(String v){return v.trim().split("\\r?\\n")[0].trim();}

 /** Tag value by number without colons, preferring the stored additional fields over the raw message. */
 static Map<String,String> tags(LetterOfCredit lc){
  var out=new LinkedHashMap<String,String>();
  if(lc.getRawMessage()!=null){var m=Pattern.compile("(?s):(\\d{2}[A-Z]?):(.*?)(?=\\r?\\n:\\d{2}[A-Z]?:|\\z)").matcher(lc.getRawMessage());while(m.find())out.putIfAbsent(m.group(1),m.group(2).trim());}
  if(lc.getAdditionalFields()!=null)lc.getAdditionalFields().forEach((k,v)->{if(v!=null&&!v.isBlank())out.put(k.replace(":","").trim(),v.trim());});
  return out;
 }
}
