package de.ostms.lc.document.service;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative text/OCR stamp hints, never proof that a document is an original. */
public final class DocumentCopyDetector {
 private static final String ORIGINAL="[o0]r[il1]g[il1]n[a4][il1]";
 private static final String KIND="("+ORIGINAL+"|copy|kopie|abschrift|duplicate|duplikat)";
 private static final String OF="(?:[\\t ]*+(?:/|of|von)[\\t ]*+[1-9])?";
 private static final Pattern MARK=Pattern.compile("(?i)^(?:([1-3])(?:st|nd|rd)?"+OF+"[\\t ]++)?"+KIND+"(?:[\\t ]++(?:no\\.?[\\t ]*+)?([1-3])"+OF+")?[\\t ]*+$");
 private static final Pattern NEGOTIABLE=Pattern.compile("(?i)[\\t ]*+[-–/,]?[\\t ]*+(?:non|not)[\\t -]*+negotiable[\\t ]*+");
 private static final Pattern SEGMENT=Pattern.compile("[\\t ]+[-–—/|:][\\t ]+|[()\\[\\]|]");
 private static final Pattern EDGE=Pattern.compile("^[\\s*_=~\"'.:;|<>#+-]+|[\\s*_=~\"'.:;|<>#+-]+$");
 private static final Pattern SPACED=Pattern.compile("^(?:\\p{L} ){3,}\\p{L}$");
 public record Hint(String kind,Integer copyNumber,String evidence){}
 private DocumentCopyDetector(){}
 private static String clean(String value){return EDGE.matcher(value).replaceAll("").trim();}
 public static Hint detect(String text){
  if(text==null||text.isBlank())return new Hint("UNKNOWN",null,"");
  Set<String> kinds=new HashSet<>();Set<Integer> numbers=new HashSet<>();
  String bounded=text.substring(0,Math.min(text.length(),100_000));
  for(String line:bounded.split("\\R")){
   if(line.length()>100)continue;
   var candidates=new ArrayList<String>();candidates.add(line);
   if(SEGMENT.matcher(line).find())candidates.addAll(Arrays.asList(SEGMENT.split(line)));
   for(String candidate:candidates){
    String stamp=clean(candidate);if(stamp.isEmpty()||stamp.length()>60)continue;
    stamp=clean(NEGOTIABLE.matcher(stamp).replaceAll(" "));
    if(SPACED.matcher(stamp).matches())stamp=stamp.replace(" ","");
    stamp=stamp.replaceFirst("(?i)^first[\\t ]++","1 ").replaceFirst("(?i)^second[\\t ]++","2 ").replaceFirst("(?i)^third[\\t ]++","3 ");
    var matcher=MARK.matcher(stamp);if(!matcher.matches())continue;
    boolean original=matcher.group(2).toLowerCase(Locale.ROOT).matches(ORIGINAL);
    kinds.add(original?"ORIGINAL":"COPY");
    Integer ordinal=matcher.group(3)!=null?Integer.valueOf(matcher.group(3)):matcher.group(1)!=null?Integer.valueOf(matcher.group(1)):null;
    if(original)numbers.add(ordinal==null?0:-ordinal);
    else if(ordinal!=null)numbers.add(ordinal);
   }
  }
  if(kinds.size()>1||numbers.size()>1)return new Hint("CONFLICT",null,"Widersprüchliche Original-/Copy-Kennzeichnungen – bitte prüfen.");
  if(kinds.isEmpty())return new Hint("UNKNOWN",null,"");
  String kind=kinds.iterator().next();Integer number=numbers.isEmpty()?null:numbers.iterator().next();
  return new Hint(kind,number,(kind.equals("ORIGINAL")?number==null||number==0?"Original":"Original "+(-number):number==null?"Copy (Nummer nicht erkannt)":"Copy "+number)+" als Text-/OCR-Kennzeichnung erkannt – bitte prüfen; kein Echtheitsnachweis.");
 }
}
