package de.ostms.lc.guarantee;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Compares the text read from a copy of the guarantee (or of the bank's advice) with the file's data. A hint only: nothing is changed, and a field the text does not show is "unknown", not a deviation. */
public final class GuaranteeCopyCheck {
 public record Item(String field,String expected,String found,String result){}
 public record Expected(String currency,BigDecimal amount,String beneficiary,String bank,LocalDate expiryDate,String reference){}
 private static final Pattern NUMBER=Pattern.compile("(?<![\\d.,])\\d{1,3}(?:[.,' ]\\d{3})+(?:[.,]\\d{1,2})?(?![\\d])|(?<![\\d.,])\\d+(?:[.,]\\d{1,2})?(?![\\d])");
 private static final Pattern NUM_DATE=Pattern.compile("\\b(\\d{1,2})[./](\\d{1,2})[./](\\d{4})\\b|\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
 private static final Pattern WORD_DATE=Pattern.compile("\\b(\\d{1,2})\\.?\\s+([A-Za-z\\u00e4\\u00c4]+)\\.?,?\\s+(\\d{4})\\b");
 private static final Pattern SWIFT_DATE=Pattern.compile(":3[0-9][A-Z]?:\\s*(\\d{6})");
 private static final Map<String,Integer> MONTHS=new HashMap<>();
 static{String[] de={"januar","februar","maerz","april","mai","juni","juli","august","september","oktober","november","dezember"},en={"january","february","march","april","may","june","july","august","september","october","november","december"};
  for(int i=0;i<12;i++){MONTHS.put(de[i],i+1);MONTHS.put(en[i],i+1);MONTHS.put(en[i].substring(0,3),i+1);}MONTHS.put("mrz",3);MONTHS.put("okt",10);MONTHS.put("dez",12);MONTHS.put("sept",9);}
 private GuaranteeCopyCheck(){}

 static String norm(String s){if(s==null)return "";var n=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace("ß","ss");return n.replaceAll("[^a-z0-9]+"," ").trim();}
 /** Amounts as they appear in the text; the last separator is the decimal one when 1 or 2 digits follow it. */
 static Set<BigDecimal> amounts(String text){
  var out=new HashSet<BigDecimal>();var m=NUMBER.matcher(text==null?"":text);
  while(m.find()){var raw=m.group().replace(" ","").replace("'","");int c=raw.lastIndexOf(','),d=raw.lastIndexOf('.'),last=Math.max(c,d);String digits;
   if(last>=0&&raw.length()-last-1<=2&&raw.length()-last-1>=1)digits=raw.substring(0,last).replaceAll("[.,]","")+"."+raw.substring(last+1);else digits=raw.replaceAll("[.,]","");
   try{out.add(new BigDecimal(digits).stripTrailingZeros());}catch(NumberFormatException ignored){}}
  return out;
 }
 static Set<LocalDate> dates(String text){
  var out=new HashSet<LocalDate>();if(text==null)return out;
  var m=NUM_DATE.matcher(text);while(m.find()){try{out.add(m.group(1)!=null?LocalDate.of(Integer.parseInt(m.group(3)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(1))):LocalDate.of(Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)),Integer.parseInt(m.group(6))));}catch(RuntimeException ignored){}}
  m=WORD_DATE.matcher(text);while(m.find()){var month=MONTHS.get(norm(m.group(2)).replace(" ",""));if(month!=null)try{out.add(LocalDate.of(Integer.parseInt(m.group(3)),month,Integer.parseInt(m.group(1))));}catch(RuntimeException ignored){}}
  m=SWIFT_DATE.matcher(text);while(m.find()){var v=m.group(1);try{out.add(LocalDate.of(2000+Integer.parseInt(v.substring(0,2)),Integer.parseInt(v.substring(2,4)),Integer.parseInt(v.substring(4,6))));}catch(RuntimeException ignored){}}
  return out;
 }
 /** The name up to the first comma, without legal-form noise words, is what must appear in the text. */
 static boolean mentions(String text,String name){
  if(name==null||name.isBlank())return true;var head=norm(name.split(",")[0]);var body=" "+norm(text)+" ";if(head.isBlank())return true;
  if(body.contains(" "+head+" "))return true;
  var words=Arrays.stream(head.split(" ")).filter(w->w.length()>2&&!Set.of("gmbh","ag","kg","ltd","llc","oy","sa","inc","the","und","and","bank","co").contains(w)).toList();
  return !words.isEmpty()&&words.stream().allMatch(w->body.contains(" "+w)||body.contains(w));
 }
 public static List<Item> compare(Expected e,String text){
  var items=new ArrayList<Item>();boolean empty=text==null||text.isBlank();
  String cur=e.currency()==null?"":e.currency().toUpperCase(Locale.ROOT);
  items.add(new Item("currency",cur,null,empty?"UNKNOWN":(Pattern.compile("\\b"+Pattern.quote(cur)+"\\b").matcher(text).find()||(cur.equals("EUR")&&text.contains("€"))||(cur.equals("USD")&&text.contains("$")))?"OK":"DEVIATION"));
  var found=empty?Set.<BigDecimal>of():amounts(text);var want=e.amount()==null?null:e.amount().stripTrailingZeros();
  String foundAmount=null;if(want!=null&&!found.isEmpty()&&!found.contains(want)){foundAmount=found.stream().max(Comparator.naturalOrder()).map(BigDecimal::toPlainString).orElse(null);}
  items.add(new Item("amount",e.amount()==null?"":e.amount().toPlainString(),foundAmount,empty||want==null?"UNKNOWN":found.contains(want)?"OK":"DEVIATION"));
  items.add(new Item("beneficiary",e.beneficiary(),null,empty?"UNKNOWN":mentions(text,e.beneficiary())?"OK":"DEVIATION"));
  items.add(new Item("bank",e.bank(),null,empty?"UNKNOWN":mentions(text,e.bank())?"OK":"DEVIATION"));
  var ds=empty?Set.<LocalDate>of():dates(text);
  items.add(new Item("expiry",e.expiryDate()==null?"":e.expiryDate().toString(),null,empty||e.expiryDate()==null?"UNKNOWN":ds.isEmpty()?"UNKNOWN":ds.contains(e.expiryDate())?"OK":"DEVIATION"));
  return items;
 }
}
