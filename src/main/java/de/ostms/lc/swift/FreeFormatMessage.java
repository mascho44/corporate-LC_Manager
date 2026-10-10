package de.ostms.lc.swift;
import java.util.*;
import java.util.regex.*;

/** MT199 / MT799: free-format messages with a reference (:20:), an optional related reference (:21:) and a narrative (:79:). */
public final class FreeFormatMessage {
 public record Parsed(String type,String reference,String relatedReference,String narrative){}
 private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
 public static final int MAX_NARRATIVE=20000;
 private FreeFormatMessage(){}
 public static Parsed parse(String type,String raw){
  var f=new LinkedHashMap<String,String>();Matcher m=FIELD.matcher(raw.strip());
  while(m.find())f.putIfAbsent(m.group(1),m.group(2).trim());
  String reference=f.get("20");
  if(reference==null||reference.isBlank())throw new IllegalArgumentException(type+" field :20: is required");
  String narrative=f.get("79");
  if(narrative==null||narrative.isBlank())throw new IllegalArgumentException("Pflichtfeld :79: (Mitteilungstext) fehlt.");
  if(narrative.length()>MAX_NARRATIVE)throw new IllegalArgumentException("Der Mitteilungstext in :79: ist zu lang.");
  String related=f.get("21");
  return new Parsed(type,clip(reference),related==null||related.isBlank()?null:clip(related),narrative);
 }
 private static String clip(String s){String t=s.trim().replaceAll("\\s+"," ");return t.length()>35?t.substring(0,35):t;}
}
