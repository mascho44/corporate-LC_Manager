package de.ostms.lc.document.service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative issue-date recognition: no filename, arrival, shipment or expiry-date fallback. */
public final class DocumentDateDetector {
 private DocumentDateDetector(){}
 private static final String TOKEN="(?:[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}|[0-9]{1,2}[./-][0-9]{1,2}[./-][0-9]{4}|[0-9]{1,2}(?:st|nd|rd|th|\\.)?[ \\-]+[A-Za-zÄäÖöÜü.]{3,12}[ ,\\-]+[0-9]{4}|[A-Za-zÄäÖöÜü.]{3,12}[ ]+[0-9]{1,2}(?:st|nd|rd|th)?[,]?[ ]+[0-9]{4})";
 private static final Pattern SPECIFIC=Pattern.compile("(?im)\\b(?:invoice date|document date|issue date|date of issue|date of issuance|issued on|certificate date|packing list date|rechnungsdatum|dokumentdatum|ausstellungsdatum|belegdatum)\\b[\\t ]*[:\\-]?[\\t ]*(?:\\n[\\t ]*)?("+TOKEN+")(?![0-9])");
 private static final Pattern GENERIC=Pattern.compile("(?im)^[\\t ]*(?:date|datum)\\b[\\t ]*[:\\-]?[\\t ]*(?:\\n[\\t ]*)?("+TOKEN+")(?![0-9])");
 private static final Map<String,Integer> MONTHS=new HashMap<>();
 static{
  String[][] names={{"jan","january","januar"},{"feb","february","februar"},{"mar","march","mär","märz","maerz"},{"apr","april"},{"may","mai"},{"jun","june","juni"},{"jul","july","juli"},{"aug","august"},{"sep","sept","september"},{"oct","october","okt","oktober"},{"nov","november"},{"dec","december","dez","dezember"}};
  for(int i=0;i<names.length;i++)for(String name:names[i])MONTHS.put(name,i+1);
 }
 public record Result(LocalDate date,String status){}
 public static Result detect(String source){
  if(source==null||source.isBlank())return new Result(null,"NOT_FOUND");
  String text=source.substring(0,Math.min(source.length(),100_000)).replace('\u00a0',' ').replace("\r\n","\n").replace('\r','\n');
  var dates=new HashSet<LocalDate>();boolean found=false,invalid=false;
  for(var pattern:List.of(SPECIFIC,GENERIC)){
   var matcher=pattern.matcher(text);
   while(matcher.find()){found=true;var date=parse(matcher.group(1));if(date==null)invalid=true;else dates.add(date);}
  }
  if(dates.size()>1)return new Result(null,"AMBIGUOUS");
  if(invalid)return new Result(null,"REVIEW");
  return dates.size()==1?new Result(dates.iterator().next(),"DETECTED"):new Result(null,found?"REVIEW":"NOT_FOUND");
 }
 private static LocalDate parse(String value){
  try{
   int day,month,year;
   if(value.matches("[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}")){
    var parts=value.split("[-/]");year=Integer.parseInt(parts[0]);month=Integer.parseInt(parts[1]);day=Integer.parseInt(parts[2]);
   }else if(value.matches("[0-9]{1,2}[./-][0-9]{1,2}[./-][0-9]{4}")){
    var parts=value.split("[./-]");int first=Integer.parseInt(parts[0]),second=Integer.parseInt(parts[1]);year=Integer.parseInt(parts[2]);
    if(value.contains(".")||first>12){day=first;month=second;}
    else if(second>12){month=first;day=second;}
    else if(first==second){day=first;month=second;}
    else return null; // Ambiguous DD/MM versus MM/DD, never guess.
   }else{
    String normalized=value.toLowerCase(Locale.ROOT).replaceAll("([0-9])(st|nd|rd|th)\\b","$1").replaceAll("[.,-]"," ").replaceAll("\\s+"," ").trim();
    var parts=normalized.split(" ");if(parts.length!=3)return null;
    if(parts[0].matches("[0-9]+")){day=Integer.parseInt(parts[0]);month=Objects.requireNonNull(MONTHS.get(parts[1]));}
    else{month=Objects.requireNonNull(MONTHS.get(parts[0]));day=Integer.parseInt(parts[1]);}
    year=Integer.parseInt(parts[2]);
   }
   if(year<1900||year>2199)return null;return LocalDate.of(year,month,day);
  }catch(RuntimeException invalid){return null;}
 }
}
