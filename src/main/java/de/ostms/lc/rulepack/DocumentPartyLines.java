package de.ostms.lc.rulepack;

import java.util.*;
import java.util.regex.Pattern;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/**
 * Issuer, recipient and goods description from the printed layout of a document: labelled lines first, otherwise the usual positions (letterhead on top,
 * the next company as addressee, the article column of the item table). Only proposals; ambiguous labels give nothing.
 */
public final class DocumentPartyLines {
 public record Fact(Field field,String value,String source){}
 private static final Pattern LEGAL_FORM=Pattern.compile("(?i)(?<![\\p{L}])(gmbh|mbh|ag|kg|ohg|ug|se|e\\.?\\s?k\\.?|ltd\\.?|limited|llc|inc\\.?|corp\\.?|corporation|co\\.|company|s\\.a\\.|s\\.?r\\.?l\\.?|b\\.v\\.|n\\.v\\.|pte|pty|plc|oy|a/s|s\\.p\\.a\\.|sarl|co\\.,?\\s?ltd\\.?)(?![\\p{L}])");
 private static final Pattern ISSUER_LABEL=Pattern.compile("(?i)^[\\t ]*(?:issued\\s+by|issuer|seller|exporter|shipper|supplier|aussteller|verk(?:ä|ae)ufer|lieferant|absender)\\b[\\t ]*[:\\-]?[\\t ]*(.*)$");
 private static final Pattern RECIPIENT_LABEL=Pattern.compile("(?i)^[\\t ]*(?:sold\\s+to|bill\\s+to|invoice\\s+to|buyer|consignee|ship\\s+to|customer|messrs\\.?|rechnungsempf(?:ä|ae)nger|k(?:ä|ae)ufer|empf(?:ä|ae)nger|kunde)\\b[\\t ]*[:\\-]?[\\t ]*(.*)$");
 private static final Pattern GOODS_LABEL=Pattern.compile("(?i)^[\\t ]*(?:description\\s+of\\s+goods|goods\\s+description|commodity|warenbezeichnung|warenbeschreibung|beschreibung\\s+der\\s+waren)\\b[\\t ]*[:\\-]?[\\t ]*(.*)$");
 private static final Pattern ITEM_HEADER_DESCRIPTION=Pattern.compile("(?i)\\b(?:article|description|beschreibung|artikel|bezeichnung)\\b");
 private static final Pattern ITEM_HEADER_QUANTITY=Pattern.compile("(?i)\\b(?:quantity|qty|menge|anzahl)\\b");
 private static final Pattern STOP=Pattern.compile("(?i)^\\s*(?:total|sum|gesamt|summe|payment|zahlung|terms|incoterm|delivery|lieferung|bank)\\b");
 private static final Pattern UNIT_WORD=Pattern.compile("(?i)^(?:pcs?|pieces?|st(?:ü|ue)ck|stk|kg|kgs|g|mt|t|ctn|ctns|cartons?|pkgs?|sets?|eur|usd|gbp|chf|cny|jpy|nos?)\\.?$");

 private DocumentPartyLines(){}

 public static List<Fact> detect(String text){
  var out=new ArrayList<Fact>();
  if(text==null||text.isBlank())return out;
  String[] lines=Arrays.stream(text.split("\\R")).map(String::strip).filter(l->!l.isEmpty()).toArray(String[]::new);
  String issuer=labelledName(lines,ISSUER_LABEL);String issuerSource="Aussteller (Beschriftung) im Text erkannt";
  if(issuer==null){issuer=letterhead(lines);issuerSource="Aussteller im Briefkopf (erste Firma) erkannt – bitte prüfen";}
  if(issuer!=null)out.add(new Fact(Field.DOCUMENT_ISSUER,issuer,issuerSource));
  String recipient=labelledName(lines,RECIPIENT_LABEL);String recipientSource="Empfänger (Beschriftung) im Text erkannt";
  if(recipient==null){recipient=addressee(lines,issuer);recipientSource="Empfänger als nächste Firma nach dem Aussteller erkannt – bitte prüfen";}
  if(recipient!=null&&!recipient.equalsIgnoreCase(issuer))out.add(new Fact(Field.DOCUMENT_RECIPIENT,recipient,recipientSource));
  String goods=labelledGoods(lines);String goodsSource="Warenbeschreibung (Beschriftung) im Text erkannt";
  if(goods==null){goods=tableGoods(lines);goodsSource="Warenbeschreibung aus der Artikelspalte erkannt – bitte prüfen";}
  if(goods!=null)out.add(new Fact(Field.DOCUMENT_GOODS_DESCRIPTION,goods,goodsSource));
  return out;
 }

 static boolean companyLine(String line){
  if(line.length()<3||line.length()>80||line.contains(":"))return false;
  long letters=line.chars().filter(Character::isLetter).count();
  if(letters<3||letters*2<line.length())return false;
  return LEGAL_FORM.matcher(line).find();
 }
 /** The value of one distinct labelled name (same line, else next line); null when absent or contradictory. */
 private static String labelledName(String[] lines,Pattern label){
  var values=new LinkedHashSet<String>();
  for(int i=0;i<lines.length;i++){
   var m=label.matcher(lines[i]);if(!m.matches())continue;
   String value=m.group(1).strip();if(value.isEmpty()&&i+1<lines.length)value=lines[i+1];
   value=value.replaceAll("\\s{2,}"," ").strip();
   if(value.length()>=3&&value.length()<=80&&value.chars().filter(Character::isLetter).count()>=3&&!value.matches(".*\\d{5,}.*"))values.add(value);
  }
  return values.size()==1?values.iterator().next():null;
 }
 private static String letterhead(String[] lines){
  for(int i=0;i<Math.min(lines.length,12);i++)if(companyLine(lines[i]))return lines[i];
  return null;
 }
 private static String addressee(String[] lines,String issuer){
  boolean after=issuer==null;
  for(int i=0;i<Math.min(lines.length,30);i++){
   if(!after){if(lines[i].equals(issuer))after=true;continue;}
   if(companyLine(lines[i])&&!lines[i].equalsIgnoreCase(issuer))return lines[i];
  }
  return null;
 }
 private static String labelledGoods(String[] lines){
  var values=new LinkedHashSet<String>();
  for(int i=0;i<lines.length;i++){
   var m=GOODS_LABEL.matcher(lines[i]);if(!m.matches())continue;
   var parts=new ArrayList<String>();if(!m.group(1).isBlank())parts.add(m.group(1).strip());
   for(int j=i+1;j<lines.length&&parts.size()<4;j++){
    if(STOP.matcher(lines[j]).find()||GOODS_LABEL.matcher(lines[j]).matches()||lines[j].matches("(?i)^[\\p{L} ./()-]{2,30}:.*"))break;
    parts.add(lines[j]);
   }
   String joined=String.join(" ",parts).replaceAll("\\s{2,}"," ").strip();
   if(joined.length()>=3&&joined.chars().filter(Character::isLetter).count()*2>=joined.length())values.add(joined.length()>300?joined.substring(0,300):joined);
  }
  return values.size()==1?values.iterator().next():null;
 }
 /** The words of the first item row under a header that names an article/description column and a quantity column (codes, numbers and units dropped). */
 private static String tableGoods(String[] lines){
  for(int i=0;i<lines.length;i++){
   if(!ITEM_HEADER_DESCRIPTION.matcher(lines[i]).find()||!ITEM_HEADER_QUANTITY.matcher(lines[i]).find())continue;
   for(int j=i+1;j<Math.min(lines.length,i+6);j++){
    if(STOP.matcher(lines[j]).find())break;
    var words=new ArrayList<String>();
    for(String token:lines[j].split("\\s+"))if(token.matches("\\p{L}[\\p{L}'&-]*")&&!UNIT_WORD.matcher(token).matches())words.add(token);
    if(words.size()>=2&&words.size()<=12)return String.join(" ",words);
   }
  }
  return null;
 }
}
