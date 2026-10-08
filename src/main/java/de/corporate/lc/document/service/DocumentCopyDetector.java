package de.corporate.lc.document.service;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative text/OCR stamp hints, never proof that a document is an original. */
public final class DocumentCopyDetector {
 private static final Pattern MARK=Pattern.compile("(?i)^(?:[1-3](?:st|nd|rd)?[\\t ]++)?(original|copy|kopie)(?:[\\t ]++(?:no\\.?[\\t ]*+)?([1-3]))?[\\t ]*+$");
 public record Hint(String kind,Integer copyNumber,String evidence){}
 private DocumentCopyDetector(){}
 public static Hint detect(String text){
  if(text==null||text.isBlank())return new Hint("UNKNOWN",null,"");
  Set<String> kinds=new HashSet<>();Set<Integer> numbers=new HashSet<>();
  String bounded=text.substring(0,Math.min(text.length(),100_000));
  for(String line:bounded.split("\\R")){
   String stamp=line.trim();if(stamp.length()>80)continue;
   var matcher=MARK.matcher(stamp);if(!matcher.matches())continue;
   boolean original=matcher.group(1).equalsIgnoreCase("original");
   kinds.add(original?"ORIGINAL":"COPY");
   if(original)numbers.add(0);
   else if(matcher.group(2)!=null)numbers.add(Integer.valueOf(matcher.group(2)));
   else if(stamp.charAt(0)>='1'&&stamp.charAt(0)<='3')numbers.add(stamp.charAt(0)-'0');
  }
  if(kinds.size()>1||numbers.size()>1)return new Hint("CONFLICT",null,"Widersprüchliche Original-/Copy-Kennzeichnungen – bitte prüfen.");
  if(kinds.isEmpty())return new Hint("UNKNOWN",null,"");
  String kind=kinds.iterator().next();Integer number=numbers.isEmpty()?null:numbers.iterator().next();
  return new Hint(kind,number,(kind.equals("ORIGINAL")?"Original":number==null?"Copy (Nummer nicht erkannt)":"Copy "+number)+" als Text-/OCR-Kennzeichnung erkannt – bitte prüfen; kein Echtheitsnachweis.");
 }
}
