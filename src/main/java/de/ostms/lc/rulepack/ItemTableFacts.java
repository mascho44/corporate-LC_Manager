package de.ostms.lc.rulepack;

import java.util.*;
import java.util.regex.Pattern;

/**
 * The first item row of an invoice-like table (header with a quantity column and a price column, then a row such as "FW26 Women Clothing 931 pcs 76,08 70.831,40 EUR"):
 * quantity and unit, unit price and total with currency. Used where the same data is not printed as "Quantity: 931".
 */
public final class ItemTableFacts {
 public record Row(String quantity,String unit,String unitPrice,String total,String currency){}
 private static final Pattern HEADER_QUANTITY=Pattern.compile("(?i)\\b(?:quantity|qty|menge|anzahl)\\b");
 private static final Pattern HEADER_PRICE=Pattern.compile("(?i)\\b(?:price|preis|amount|betrag)\\b");
 private static final Pattern STOP=Pattern.compile("(?i)^\\s*(?:total|sum|gesamt|summe|subtotal)\\b");
 private static final Pattern NUMBER=Pattern.compile("\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,4})?|\\d+(?:[.,]\\d{1,4})?");
 private static final Pattern UNIT=Pattern.compile("(?i)^(?:pcs?|pieces?|sets?|units?|kgs?|mt|tons?|tonnes|cartons?|ctns?|pkgs?|bags?|drums?|ltrs?|m2|m3|st(?:ü|ue)ck|stk)\\.?$");
 private static final Pattern CURRENCY=Pattern.compile("^(?:EUR|USD|GBP|CHF|JPY|CNY|HKD)$");
 private ItemTableFacts(){}

 public static Optional<Row> detect(String text){
  if(text==null||text.isBlank())return Optional.empty();
  String[] lines=Arrays.stream(text.split("\\R")).map(String::strip).filter(l->!l.isEmpty()).toArray(String[]::new);
  for(int i=0;i<lines.length;i++){
   if(!HEADER_QUANTITY.matcher(lines[i]).find()||!HEADER_PRICE.matcher(lines[i]).find())continue;
   for(int j=i+1;j<Math.min(lines.length,i+7);j++){
    if(STOP.matcher(lines[j]).find())continue;
    var row=parse(lines[j]);if(row!=null)return Optional.of(row);
   }
  }
  return Optional.empty();
 }
 private static Row parse(String line){
  String[] tokens=line.split("\\s+");
  for(int k=0;k+1<tokens.length;k++){
   if(!NUMBER.matcher(tokens[k]).matches()||!UNIT.matcher(tokens[k+1]).matches())continue;
   String quantity=DocumentFactSuggester.normalizeNumber(tokens[k]);if(quantity==null)continue;
   var prices=new ArrayList<String>();String currency=null;
   for(int m=k+2;m<tokens.length;m++){
    String t=tokens[m].toUpperCase(Locale.ROOT);
    if(CURRENCY.matcher(t).matches())currency=t;
    else if(NUMBER.matcher(tokens[m]).matches()){String n=DocumentFactSuggester.normalizeNumber(tokens[m]);if(n!=null)prices.add(n);}
   }
   if(currency==null)for(int m=0;m<k;m++)if(CURRENCY.matcher(tokens[m].toUpperCase(Locale.ROOT)).matches())currency=tokens[m].toUpperCase(Locale.ROOT);
   String unit=tokens[k+1].toUpperCase(Locale.ROOT).replaceAll("\\.$","");
   if(unit.equals("PC")||unit.equals("PIECE")||unit.equals("PIECES"))unit="PCS";
   return new Row(quantity,unit,prices.size()>=2?prices.get(0):null,prices.isEmpty()?null:prices.get(prices.size()-1),currency);
  }
  return null;
 }
}
