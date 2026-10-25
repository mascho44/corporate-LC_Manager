package de.ostms.lc.document.service;
import java.util.*;
import java.util.regex.*;
/** Labelled table rows without punctuation are not free-text inference. */
public final class AdvisingRows {
 public record Row(String label,String value,String target){}
 private static final Map<String,String> LABELS=new LinkedHashMap<>();
 static{
  add("reference","Akkreditivnummer","LC reference","LC number","Credit number","Documentary credit number");
  add("ownBankReference","Referenz eigene Bank","Own bank reference");
  add("foreignBankReference","Fremdbankreferenz","Referenz der eröffnenden Bank","Issuing bank reference","Their reference");
  add("","Unsere Referenz","Ihre Referenz","Our reference","Your reference");
  add("amount","Akkreditiv über","Akkreditivbetrag","LC amount","Credit amount","Betrag","Amount");
  add("expiryDate","Gültig bis","Verfallsdatum","Expiry date","Date of expiry");
  add("issuingBank","Eröffnende Bank","Issuing bank");add("applicant","Auftraggeber","Antragsteller","Applicant");add("beneficiary","Begünstigter","Beneficiary");
 }
 private static void add(String target,String... labels){for(String label:labels)LABELS.put(label,target);}
 public static List<Row> read(String text){
  String[] lines=Arrays.stream((text==null?"":text).split("\\R")).filter(line->!line.isBlank()).toArray(String[]::new);List<Row> rows=new ArrayList<>();
  for(int i=0;i<lines.length&&rows.size()<100;i++){
   String line=lines[i].trim();boolean found=false;
   for(var entry:LABELS.entrySet()){
    var matcher=Pattern.compile("(?i)^"+Pattern.quote(entry.getKey())+"(?:\\s*[:：]\\s*|[ \\t]+)(\\S.*)$").matcher(line);
    if(matcher.matches()){rows.add(new Row(entry.getKey(),matcher.group(1).trim(),entry.getValue()));found=true;break;}
    if(line.equalsIgnoreCase(entry.getKey())&&i+1<lines.length){String next=lines[i+1].trim();if(!next.isBlank()&&LABELS.keySet().stream().noneMatch(label->next.toLowerCase(Locale.ROOT).startsWith(label.toLowerCase(Locale.ROOT)))){rows.add(new Row(entry.getKey(),next,entry.getValue()));i++;found=true;break;}}
   }
   if(!found&&!line.matches("(?i).*(?:Telefon|Telefax|E-Mail|Website|BIC|BLZ|Vorstand|Steuer-Nr|USt|Bankleitzahl|Vorsitzender|Verwaltungsrats).*")){
    var matcher=Pattern.compile("^([^:]{1,100}):[ \\t]*(\\S.*)$").matcher(line);if(matcher.matches())rows.add(new Row(matcher.group(1).trim(),matcher.group(2).trim(),""));
   }
  }
  if((text==null?"":text).contains("zu Ihren Gunsten")){
   for(int start=0;start<lines.length;start++)if(lines[start].trim().startsWith("Kreissparkasse ")){
    List<String> address=new ArrayList<>();int end=start+1;
    while(end<lines.length&&end<start+8&&!lines[end].contains("Abwicklung im Auftrag")){if(!lines[end].isBlank())address.add(lines[end].trim());end++;}
    if(end<lines.length&&lines[end].contains("Abwicklung im Auftrag")&&address.size()>=2&&address.size()<=5){rows.add(new Row("Empfänger (Briefkopf – prüfen)",String.join("\n",address),"beneficiary"));break;}
   }
  }
  return rows;
 }
 private AdvisingRows(){}
}
