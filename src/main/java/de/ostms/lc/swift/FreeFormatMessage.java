package de.ostms.lc.swift;
import java.util.*;
import java.util.regex.*;

/** MT199 / MT799: free-format messages with a reference (:20:), an optional related reference (:21:) and a narrative (:79:). MT701, MT734 and MT750 are structured: their fields are kept by code and rendered as a labelled narrative. */
public final class FreeFormatMessage {
 public record Parsed(String type,String reference,String relatedReference,String narrative,Map<String,String> fields){
  public Parsed(String type,String reference,String relatedReference,String narrative){this(type,reference,relatedReference,narrative,Map.of());}
 }
 public static boolean structured(String type){return LABELS.containsKey(type);}
 public static boolean supports(String type){return type.equals("MT199")||type.equals("MT799")||structured(type);}
 /** German name of a field of a structured message, or null. */
 public static String label(String type,String code){var m=LABELS.get(type);return m==null?null:m.get(code);}
 private static final Map<String,Map<String,String>> LABELS=Map.of(
  "MT734",Map.ofEntries(Map.entry("20","Referenz des Absenders"),Map.entry("21","Referenz der einreichenden Bank"),Map.entry("32A","Datum, Währung und Betrag der Einreichung"),Map.entry("33B","Zusätzlicher Betrag"),Map.entry("73A","Zusätzlich zu zahlende Gebühren"),Map.entry("77J","Abweichungen"),Map.entry("77B","Verfügung über die Dokumente"),Map.entry("72Z","Mitteilung an den Empfänger")),
  "MT750",Map.ofEntries(Map.entry("20","Referenz des Absenders"),Map.entry("21","Referenz der einreichenden Bank"),Map.entry("32B","Hauptbetrag"),Map.entry("33B","Erhöhter Betrag"),Map.entry("34B","Neuer Betrag"),Map.entry("71D","Abgezogene Gebühren"),Map.entry("73A","Zusätzlich zu zahlende Gebühren"),Map.entry("57A","Beratende Bank"),Map.entry("77J","Abweichungen"),Map.entry("72Z","Mitteilung an den Empfänger")),
  "MT701",Map.ofEntries(Map.entry("27","Folgenummer"),Map.entry("20","Akkreditivnummer"),Map.entry("45A","Waren und Leistungen"),Map.entry("46A","Geforderte Dokumente"),Map.entry("47A","Zusätzliche Bedingungen"),Map.entry("49G","Besondere Zahlungsbedingungen für den Begünstigten"),Map.entry("49H","Besondere Zahlungsbedingungen für die Zahlstelle")));
 private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
 public static final int MAX_NARRATIVE=20000;
 private FreeFormatMessage(){}
 public static Parsed parse(String type,String raw){
  var f=new LinkedHashMap<String,String>();Matcher m=FIELD.matcher(raw.strip());
  while(m.find())f.putIfAbsent(m.group(1),m.group(2).trim());
  if(structured(type))return parseStructured(type,f);
  String reference=f.get("20");
  if(reference==null||reference.isBlank())throw new IllegalArgumentException(type+" field :20: is required");
  String narrative=f.get("79");
  if(narrative==null||narrative.isBlank())throw new IllegalArgumentException("Pflichtfeld :79: (Mitteilungstext) fehlt.");
  if(narrative.length()>MAX_NARRATIVE)throw new IllegalArgumentException("Der Mitteilungstext in :79: ist zu lang.");
  String related=f.get("21");
  return new Parsed(type,clip(reference),related==null||related.isBlank()?null:clip(related),narrative);
 }
 private static Parsed parseStructured(String type,Map<String,String> f){
  String reference=f.get("20");
  if(reference==null||reference.isBlank())throw new IllegalArgumentException("Pflichtfeld :20: ("+LABELS.get(type).get("20")+") fehlt.");
  if(type.equals("MT701")){
   if(f.get("27")==null||f.get("27").isBlank())throw new IllegalArgumentException("Pflichtfeld :27: (Folgenummer) fehlt.");
   if(List.of("45A","46A","47A").stream().noneMatch(c->f.get(c)!=null&&!f.get(c).isBlank()))throw new IllegalArgumentException("Mindestens eines der Felder :45A:, :46A: oder :47A: fehlt.");
  }else if(f.get("77J")==null||f.get("77J").isBlank())throw new IllegalArgumentException("Pflichtfeld :77J: (Abweichungen) fehlt.");
  var text=new StringBuilder();
  f.forEach((code,value)->{String label=LABELS.get(type).get(code);text.append(':').append(code).append(": ").append(label==null?"Weiteres Feld":label).append('\n').append(value).append("\n\n");});
  String narrative=text.toString().strip();
  if(narrative.length()>MAX_NARRATIVE)throw new IllegalArgumentException("Die Nachricht ist zu lang.");
  String related=f.get("21");
  return new Parsed(type,clip(reference),related==null||related.isBlank()?null:clip(related),narrative,Map.copyOf(f));
 }
 private static String clip(String s){String t=s.trim().replaceAll("\\s+"," ");return t.length()>35?t.substring(0,35):t;}
}
