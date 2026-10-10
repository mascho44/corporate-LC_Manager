package de.ostms.lc.rulepack;
import java.util.*;
import java.util.regex.*;

/** Package count and weights stated on a packing list. Only labelled values count; conflicting values yield nothing. */
public final class PackingListFacts {
 public record Facts(Integer packageCount,String packageUnit,String grossWeight,String netWeight,String weightUnit){
  public boolean empty(){return packageCount==null&&grossWeight==null&&netWeight==null;}
 }
 private static final String UNITS="packages?|pkgs?|cartons?|ctns?|pallets?|plts?|colli|kolli|cases?|boxes|bags|drums|crates|bundles|rolls|packst(?:ü|ue)cke?";
 private static final Pattern COUNT_LABELLED=Pattern.compile("(?i)(?:total\\s+)?(?:number|no\\.?|nr\\.?|qty\\.?|quantity)\\s+of\\s+("+UNITS+")\\b[^\\d\\n]{0,12}(\\d{1,6})\\b|(?:anzahl|gesamtanzahl)(?:\\s+der)?\\s+("+UNITS+")\\b[^\\d\\n]{0,12}(\\d{1,6})\\b");
 private static final Pattern COUNT_TOTAL=Pattern.compile("(?i)\\btotal\\s*(?:of\\s*)?("+UNITS+")\\b[^\\d\\n]{0,12}(\\d{1,6})\\b|\\b("+UNITS+")\\s*(?:total|gesamt|insgesamt)\\s*[:=-]\\s*(\\d{1,6})\\b|\\b("+UNITS+")\\s*[:=]\\s*(\\d{1,6})\\b");
 private static final Pattern COUNT_TOTAL_LINE=Pattern.compile("(?i)\\btotal\\b[:\\s]{1,4}(\\d{1,6})\\s*("+UNITS+")\\b");
 private static final Pattern WEIGHT=Pattern.compile("(?i)(?:%s)\\b(?:\\s*\\(\\s*(kgs?|kilos?|mt|tons?|tonnes?|t|lbs?)\\s*\\))?[^\\d\\n]{0,20}(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,3})?|\\d+)\\s{0,2}(kgs?|kilos?|mt|tons?|tonnes?|t|lbs?)?\\b");
 private static final String GROSS="(?:total\\s+)?gross\\s+weight|gross\\s+wt\\.?|g\\.\\s?w\\.?|brutto(?:gewicht)?";
 private static final String NET="(?:total\\s+)?net\\s+weight|net\\s+wt\\.?|n\\.\\s?w\\.?|netto(?:gewicht)?";
 private PackingListFacts(){}

 public static Facts detect(String text){
  if(text==null||text.isBlank())return new Facts(null,null,null,null,null);
  String source=text.length()>100_000?text.substring(0,100_000):text;
  var counts=new LinkedHashSet<Integer>();String unit=null;
  for(var p:List.of(COUNT_LABELLED,COUNT_TOTAL)){
   var m=p.matcher(source);
   while(m.find())for(int g=1;g<m.groupCount();g+=2)if(m.group(g)!=null&&m.group(g+1)!=null){counts.add(Integer.parseInt(m.group(g+1)));if(unit==null)unit=canonicalUnit(m.group(g));}
  }
  var line=COUNT_TOTAL_LINE.matcher(source);
  while(line.find()){counts.add(Integer.parseInt(line.group(1)));if(unit==null)unit=canonicalUnit(line.group(2));}
  Integer count=counts.size()==1?counts.iterator().next():null;
  var gross=weight(source,GROSS);var net=weight(source,NET);
  String weightUnit=gross!=null&&gross[1]!=null?gross[1]:net!=null?net[1]:null;
  return new Facts(count,count==null?null:unit,gross==null?null:gross[0],net==null?null:net[0],(gross==null&&net==null)?null:weightUnit);
 }

 static String canonicalUnit(String raw){
  String u=raw.toLowerCase(Locale.ROOT);
  if(u.startsWith("carton")||u.startsWith("ctn"))return "Kartons";if(u.startsWith("pallet")||u.startsWith("plt"))return "Paletten";
  if(u.startsWith("pack")||u.startsWith("pkg"))return "Packstücke";if(u.startsWith("coll")||u.startsWith("koll"))return "Kolli";
  if(u.startsWith("case"))return "Kisten";if(u.startsWith("box"))return "Boxen";if(u.startsWith("bag"))return "Säcke";if(u.startsWith("drum"))return "Fässer";
  if(u.startsWith("crate"))return "Verschläge";if(u.startsWith("bundle"))return "Bündel";if(u.startsWith("roll"))return "Rollen";return "Packstücke";
 }
 static String unit(String raw){
  if(raw==null)return null;
  return switch(raw.toLowerCase(Locale.ROOT)){case "kg","kgs","kilo","kilos"->"KG";case "mt","t","ton","tons","tonne","tonnes"->"T";case "lb","lbs"->"LB";default->null;};
 }
 /** {number, unit or null}; null unless the label occurs with exactly one value. */
 static String[] weight(String text,String label){
  var matcher=Pattern.compile(WEIGHT.pattern().replace("%s",label),Pattern.CASE_INSENSITIVE).matcher(text);
  var values=new LinkedHashSet<String>();String unit=null;
  while(matcher.find()){
   String n=normalizeNumber(matcher.group(2));if(n==null)continue;
   values.add(n);String u=unit(matcher.group(3)!=null?matcher.group(3):matcher.group(1));if(u!=null)unit=u;
  }
  return values.size()==1?new String[]{values.iterator().next(),unit}:null;
 }
 static String normalizeNumber(String raw){
  String n=raw.trim();int dot=n.lastIndexOf('.'),comma=n.lastIndexOf(',');
  if(dot>=0&&comma>=0)n=dot>comma?n.replace(",",""):n.replace(".","").replace(',','.');
  else if(comma>=0)n=n.length()-comma==4&&n.indexOf(',')==comma?n.replace(",",""):n.replace(',','.');
  else if(dot>=0&&n.length()-dot==4&&n.indexOf('.')==dot)n=n.replace(".","");
  return n.matches("\\d+(\\.\\d+)?")?n:null;
 }
}
