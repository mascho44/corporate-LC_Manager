package de.ostms.lc.rulepack;
import java.util.*;
import java.util.regex.Pattern;

/** Splits the text of a party (SWIFT :50:/:59: or a block of a document) into name, address lines and country. */
public final class PartyAddress {
 public record Parsed(String name,List<String> addressLines,String country){public String address(){return String.join(", ",addressLines);}}
 private static final Pattern NOISE=Pattern.compile("(?i)^(?:/.*|(?:tel|fax|phone|telefon|e-?mail|email|www|vat|tax|uid|ust)[\\s.:-].*|.*@.*|\\+?[\\d\\s()/.-]{7,})$");
 private PartyAddress(){}
 public static Parsed parse(String text){
  if(text==null||text.isBlank())return null;
  var lines=new ArrayList<String>();
  for(String raw:text.split("\\R")){String line=raw.strip();if(!line.isEmpty()&&!NOISE.matcher(line).matches())lines.add(line);}
  if(lines.isEmpty())return null;
  String name=lines.remove(0);
  String country=null;
  for(int i=lines.size()-1;i>=0&&country==null&&i>=lines.size()-2;i--){
   String line=lines.get(i);
   var whole=CountryResolver.resolve(line);
   if(whole.isPresent()){country=whole.get();lines.remove(i);break;}
   int comma=Math.max(line.lastIndexOf(','),Math.max(line.lastIndexOf(" - "),line.lastIndexOf(" – ")));
   if(comma>0){
    var tail=CountryResolver.resolve(line.substring(comma+(line.startsWith(" - ",comma)||line.startsWith(" – ",comma)?3:1)));
    if(tail.isPresent()){country=tail.get();lines.set(i,line.substring(0,comma).strip());}
   }
  }
  return new Parsed(name,List.copyOf(lines),country);
 }
}
