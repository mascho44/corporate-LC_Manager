package de.corporate.lc.document.service;

import java.util.*;
import java.util.regex.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.format.*;

/** Conservative labelled extraction. Ambiguous or invalid values stay empty. */
public final class AdvisingLetterExtractor {
 public record Proposal(Map<String,String> fields,Map<String,String> evidence,List<String> warnings){}
 public static Proposal extract(String text){
  Map<String,String> fields=new LinkedHashMap<>(),evidence=new LinkedHashMap<>();List<String> warnings=new ArrayList<>();
  String source=text==null?"":text;
  pick(source,"ownBankReference","(?:Referenz eigene Bank|Own bank reference)",fields,evidence,warnings);
  pick(source,"foreignBankReference","(?:Fremdbankreferenz|Referenz der eröffnenden Bank|Issuing bank reference|Their reference)",fields,evidence,warnings);
  pick(source,"reference","(?:Akkreditivnummer|LC reference|LC number|Credit number|Documentary credit number)",fields,evidence,warnings);
  pick(source,"applicant","(?:Antragsteller|Applicant|Auftraggeber)",fields,evidence,warnings);
  pick(source,"beneficiary","(?:Begünstigter|Beneficiary)",fields,evidence,warnings);
  pick(source,"expiryDate","(?:Verfallsdatum|Expiry date|Date of expiry|Gültig bis)",fields,evidence,warnings);
  pick(source,"amount","(?:Akkreditivbetrag|LC amount|Credit amount|Betrag|Amount)",fields,evidence,warnings);
  // Explicit MT700 fields embedded in an advice; no assumption about bank ownership of :20:.
  String[][] tags={{"reference","20"},{"applicant","50"},{"beneficiary","59"},{"amount","32B"},{"expiryDate","31D"}};
  for(var tag:tags){var match=Pattern.compile("(?ms)^:"+tag[1]+":(.*?)(?=^:\\d{2}[A-Z]?:|\\z)").matcher(source);List<String> values=new ArrayList<>();while(match.find())values.add(match.group(1).trim());if(values.size()==1&&!fields.containsKey(tag[0])){String value=values.get(0);if(tag[0].equals("expiryDate")){var date=Pattern.compile("^(\\d{6})(?:[^\\d].*)?$",Pattern.DOTALL).matcher(value);value=date.matches()?date.group(1):value;}fields.put(tag[0],value);evidence.put(tag[0],":"+tag[1]+":"+values.get(0));}else if(values.size()>1)warnings.add("Mehrere SWIFT-Werte für "+tag[0]+" – bitte manuell prüfen.");}
  if(fields.containsKey("amount")){
   String raw=fields.get("amount");var match=Pattern.compile("^([A-Z]{3})\\s*([0-9][0-9., ]*)$").matcher(raw.trim());
   if(match.matches()){String number=match.group(2).replace(" ","");try{if(number.contains(",")&&number.contains(".")) {if(number.lastIndexOf(',')>number.lastIndexOf('.'))number=number.replace(".","").replace(',','.');else number=number.replace(",","");}else if(number.contains(",")){if(number.matches("\\d+,\\d{1,2}"))number=number.replace(',','.');else throw new IllegalArgumentException();}else if(!number.matches("\\d+(?:\\.\\d{1,2})?"))throw new IllegalArgumentException();fields.put("amount",new BigDecimal(number).toPlainString());fields.put("currency",match.group(1));}catch(IllegalArgumentException e){fields.remove("amount");warnings.add("Betrag nicht eindeutig lesbar – manuell prüfen.");}}
   else{fields.remove("amount");warnings.add("Betrag/Währung nicht eindeutig – manuell prüfen.");}
  }
  if(fields.containsKey("expiryDate")){String raw=fields.get("expiryDate");try{LocalDate date=raw.matches("\\d{6}")?LocalDate.parse(raw,DateTimeFormatter.ofPattern("uuMMdd").withResolverStyle(ResolverStyle.STRICT)):raw.matches("\\d{2}\\.\\d{2}\\.\\d{4}")?LocalDate.parse(raw,DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)):LocalDate.parse(raw);fields.put("expiryDate",date.toString());}catch(DateTimeException e){fields.remove("expiryDate");warnings.add("Verfallsdatum nicht eindeutig – manuell prüfen.");}}
  fields.entrySet().removeIf(entry->{if(entry.getValue().length()>255){warnings.add(entry.getKey()+": zu lang für das Standardfeld – manuell kürzen.");return true;}return false;});
  if(fields.isEmpty())warnings.add("Keine unterstützten Angaben erkannt. Werte bitte am Original erfassen.");
  warnings.add("Vorschläge sind unbestätigt. Mehrzeilige Anschriften und weitere LC-Bedingungen am Original prüfen und ergänzen.");
  warnings.add("Unsere/Ihre Referenz wird ohne eindeutige Bankrolle nicht automatisch einer Bank zugeordnet.");
  return new Proposal(fields,evidence,warnings);
 }
 private static void pick(String source,String key,String label,Map<String,String> fields,Map<String,String> evidence,List<String> warnings){var matcher=Pattern.compile("(?im)^\\s*"+label+"\\s*[:：]\\s*([^\\r\\n]+)$").matcher(source);Set<String> values=new LinkedHashSet<>();String line=null;while(matcher.find()){values.add(matcher.group(1).trim());line=matcher.group().trim();}if(values.size()==1){fields.put(key,values.iterator().next());evidence.put(key,line);}else if(values.size()>1)warnings.add("Mehrdeutige Angabe: "+key+" – manuell prüfen.");}
 private AdvisingLetterExtractor(){}
}
