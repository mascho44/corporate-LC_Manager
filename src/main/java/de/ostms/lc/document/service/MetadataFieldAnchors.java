package de.ostms.lc.document.service;

import de.ostms.lc.document.api.DocumentInboxAttachRequest.Metadata;
import java.math.BigDecimal;
import java.util.*;

/** Literal line labels, never executable regex supplied by users. No training values are stored. */
final class MetadataFieldAnchors {
 record Pattern(String heading,Map<String,String> labels){}
 record Match(DocumentMetadataTraining.Confirmation values,Map<String,String> evidence){}
 static Pattern learn(String text,DocumentMetadataTraining.Confirmation confirmed){
  var labels=new LinkedHashMap<String,String>();var lines=lines(text);String heading=heading(lines);
  if(heading==null)return new Pattern(null,Map.of());
  for(String field:List.of("reference","documentNumber","amount","currency","documentDate")){
   Object expected=value(confirmed,field);if(expected==null)continue;
   var matches=new LinkedHashSet<String>();
   for(int i=0;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon<2||colon>80)continue;
    String label=lines[i].substring(0,colon).strip();if(!label.matches("[\\p{L} ./'()-]{2,80}"))continue;
    String raw=lines[i].substring(colon+1).strip();if(raw.isEmpty()&&i+1<lines.length)raw=lines[i+1].strip();
    Object actual=parse(field,raw);if(equal(expected,actual))matches.add(label);
   }
   if(matches.size()==1)labels.put(field,matches.iterator().next());
  }
  return new Pattern(heading,labels);
 }
 static Match apply(String text,Pattern pattern){
  var lines=lines(text);if(pattern.heading()==null||!pattern.heading().equals(heading(lines)))return null;
  var facts=new HashMap<String,Object>();var evidence=new LinkedHashMap<String,String>();
  for(var entry:pattern.labels().entrySet()){
   var values=new LinkedHashSet<Object>();String snippet=null;
   for(int i=0;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon<0||!lines[i].substring(0,colon).strip().equals(entry.getValue()))continue;
    String raw=lines[i].substring(colon+1).strip();String source=lines[i];if(raw.isEmpty()&&i+1<lines.length){raw=lines[i+1].strip();source+="\n"+lines[i+1];}
    Object parsed=parse(entry.getKey(),raw);if(parsed!=null){values.add(parsed);snippet=source;}
   }
   if(values.size()>1)return null;
   if(values.size()==1){facts.put(entry.getKey(),values.iterator().next());evidence.put(entry.getKey(),snippet.substring(0,Math.min(snippet.length(),600)));}
  }
  if(facts.size()<2)return null;
  return new Match(new DocumentMetadataTraining.Confirmation(new Metadata((String)facts.get("reference"),(String)facts.get("documentNumber"),(BigDecimal)facts.get("amount"),(String)facts.get("currency")),(java.time.LocalDate)facts.get("documentDate")),evidence);
 }
 private static String[] lines(String text){return (text==null?"":text.substring(0,Math.min(text.length(),100_000))).replace("\r\n","\n").split("\\R");}
 private static String heading(String[] lines){for(String line:lines){String value=line.strip();if(value.isEmpty())continue;return value.length()<=120&&value.matches("[\\p{L} ./'()-]{3,120}")?value:null;}return null;}
 private static Object value(DocumentMetadataTraining.Confirmation c,String field){return switch(field){case "reference"->c.metadata().reference();case "documentNumber"->c.metadata().documentNumber();case "amount"->c.metadata().amount();case "currency"->c.metadata().currency();default->c.documentDate();};}
 private static boolean equal(Object a,Object b){return a instanceof BigDecimal x&&b instanceof BigDecimal y?x.compareTo(y)==0:Objects.equals(a,b);}
 static Object parse(String field,String raw){
  if(raw.length()>255)return null;
  return switch(field){
   case "reference","documentNumber"->raw.matches("[A-Za-z0-9][A-Za-z0-9./_-]{2,99}")&&raw.chars().anyMatch(Character::isDigit)?raw:null;
   case "currency"->raw.matches("[A-Z]{3}")?raw:null;
   case "documentDate"->DocumentDateDetector.detect("Date: "+raw).date();
   case "amount"->{if(!raw.matches("[0-9]{1,16}(?:[.,][0-9]{1,2})?"))yield null;yield new BigDecimal(raw.replace(',','.')).stripTrailingZeros();}
   default->null;
  };
 }
}
