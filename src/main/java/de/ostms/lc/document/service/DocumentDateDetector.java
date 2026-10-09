package de.ostms.lc.document.service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative issue-date recognition: no filename, arrival, shipment or expiry-date fallback. */
public final class DocumentDateDetector {
 private DocumentDateDetector(){}
 private static final String TOKEN="(?:[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}|[0-9]{1,2}[./-][0-9]{1,2}[./-][0-9]{4}|[0-9]{1,2}(?:st|nd|rd|th|\\.)?[ \\-]+[A-Za-zÄäÖöÜü.]{3,12}[ ,\\-]+[0-9]{4}|[0-9]{1,2}[ \\-]+[A-Za-zÄäÖöÜü]{3,9}[ \\-]+[0-9]{2}(?![0-9])|[A-Za-zÄäÖöÜü.]{3,12}[ ]+[0-9]{1,2}(?:st|nd|rd|th)?[,]?[ ]+[0-9]{4})";
 private static final Pattern SPECIFIC=Pattern.compile("(?im)\\b(?:invoice date|date of invoice|invoice dated|document date|issue date|date of issue|date of issuance|date and place of issue|place and date of issue|place and date of issuance|date of signing|issued on|dated|certificate date|date of certificate|packing list date|date of packing list|rechnungsdatum|rechnung vom|dokumentdatum|ausstellungsdatum|ausgestellt am|datum der ausstellung|belegdatum|ort und datum)\\b[\\t ]*[:\\-]?[\\t ]*(?:\\n[\\t ]*)?("+TOKEN+")(?![0-9])");
 private static final Pattern GENERIC=Pattern.compile("(?im)^[\\t ]*(?:date|datum)\\b[\\t ]*[:\\-]?[\\t ]*(?:\\n[\\t ]*)?("+TOKEN+")(?![0-9])");
 private static final Map<String,Integer> MONTHS=new HashMap<>();
 static{
  String[][] names={{"jan","january","januar"},{"feb","february","februar"},{"mar","march","mär","märz","maerz"},{"apr","april"},{"may","mai"},{"jun","june","juni"},{"jul","july","juli"},{"aug","august"},{"sep","sept","september"},{"oct","october","okt","oktober"},{"nov","november"},{"dec","december","dez","dezember"}};
  for(int i=0;i<names.length;i++)for(String name:names[i])MONTHS.put(name,i+1);
 }
 public record Result(LocalDate date,String status){}
 private static final Pattern PLACE_DATE=Pattern.compile("(?im)^[\\t ]*[A-Za-zÄäÖöÜüß][A-Za-zÄäÖöÜüß .'-]{1,40},[\\t ]*(?:den[\\t ]+|the[\\t ]+|on[\\t ]+)?("+TOKEN+")(?:[\\t ]+[A-Za-zÄäÖöÜüß .]{0,30})?[\\t ]*$");
 /** "DATE:" inside a line, e.g. "SHANGHAI, CHINA DATE: 21.07.2026"; shipment, expiry and similar dates are excluded. */
 private static final Pattern MIDLINE=Pattern.compile("(?im)(?<![\\p{L}/])(?<!shipment[\\t ])(?<!shipping[\\t ])(?<!expiry[\\t ])(?<!due[\\t ])(?<!value[\\t ])(?<!b/l[\\t ])(?<!board[\\t ])(?<!latest[\\t ])(?<!maturity[\\t ])(?<!delivery[\\t ])(?<!loading[\\t ])(?<!arrival[\\t ])(?<!payment[\\t ])(?<!shipped[\\t ])(?<!ship[\\t ])\\bdate[\\t ]{0,3}:[\\t ]{0,3}("+TOKEN+")(?![0-9])");
 /** Bill of lading and waybills: the on-board date is the document date when no issue date can be read. */
 private static final Pattern ON_BOARD=Pattern.compile("(?im)\\b(?:date shipped on board|shipped on board date|shipped on board|laden on board|on board date)\\b[\\t ]{0,3}[:\\-]?[\\t ]{0,3}(?:\\n[\\t ]{0,3}){0,3}("+TOKEN+")(?![0-9])");
 private static final Pattern TRANSPORT=Pattern.compile("(?i)bill of lading|waybill|konnossement|seefrachtbrief");
 /** A date alone on its line directly above the printed caption "Place and date of issue". */
 private static final Pattern BEFORE_CAPTION=Pattern.compile("(?im)^[\\t ]*("+TOKEN+")[\\t ]*\\n(?:[^\\n]{0,80}\\n)?[\\t ]*(?:place and date|ort und datum|lieu et date)");
 /** OCR often spaces separators: "25 . 08 . 2026" becomes "25.08.2026". */
 static String tidy(String text){
  return text.replaceAll("(?<=[0-9])[\\t ]{0,3}+([./-])[\\t ]{0,3}+(?=[0-9])","$1");
 }
 /** Explicit issue-date labels outrank a bare "Date:" which outranks "City, date" lines; the first tier with a hit decides. */
 public static Result detect(String source){
  if(source==null||source.isBlank())return new Result(null,"NOT_FOUND");
  String text=tidy(source.substring(0,Math.min(source.length(),100_000)).replace('\u00a0',' ').replace("\r\n","\n").replace('\r','\n'));
  boolean anyFound=false;
  var tiers=new ArrayList<>(List.of(SPECIFIC,BEFORE_CAPTION,GENERIC,MIDLINE,PLACE_DATE));
  if(TRANSPORT.matcher(text).find())tiers.add(ON_BOARD);
  for(var pattern:tiers){
   var dates=new HashSet<LocalDate>();boolean found=false,invalid=false;
   var matcher=pattern.matcher(text);
   while(matcher.find()){found=true;var date=parse(matcher.group(1));if(date==null)invalid=true;else dates.add(date);}
   if(!found)continue;anyFound=true;
   if(dates.size()>1)return new Result(null,"AMBIGUOUS");
   if(invalid)return new Result(null,"REVIEW");
   if(dates.size()==1)return new Result(dates.iterator().next(),"DETECTED");
  }
  return new Result(null,anyFound?"REVIEW":"NOT_FOUND");
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
    year=Integer.parseInt(parts[2]);if(parts[2].length()==2)year+=2000;
   }
   if(year<1900||year>2199)return null;return LocalDate.of(year,month,day);
  }catch(RuntimeException invalid){return null;}
 }
}
