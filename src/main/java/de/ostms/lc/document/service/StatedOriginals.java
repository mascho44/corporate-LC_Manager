package de.ostms.lc.document.service;

import java.util.*;
import java.util.regex.*;

/**
 * The number of originals a bill of lading itself states: "Number of original Bills of Lading: 3 (three)" on one line, or the usual carrier table with the caption
 * "NO. OF ORIGINALS" in a header row and "3 / THREE" in the row below. A bare digit is only accepted right behind the caption; below it a number is taken only when it
 * is stated twice (digit and word agree), so voyage numbers and container numbers of the same row are not mistaken for it. Empty when unclear or contradictory.
 */
public final class StatedOriginals {
 private StatedOriginals(){}
 private static final Map<String,Integer> WORDS=Map.of("one",1,"two",2,"three",3,"four",4,"five",5,"six",6);
 private static final Pattern LABEL=Pattern.compile("(?i)(?:number|no\\.?|nbr\\.?|anzahl)[\\t ]+(?:of[\\t ]+|der[\\t ]+)?(?:original[\\t ]+)?(?:bills?[\\t ]+of[\\t ]+lading|bs?/?l|bs/l|originals?|originale)\\b");
 private static final Pattern SAME_LINE=Pattern.compile("(?i)^[\\t ]{0,3}[:\\-]?[\\t ]{0,3}\\(?[\\t ]*(one|two|three|four|five|six|[1-9])\\b");
 private static final Pattern DIGIT_WORD=Pattern.compile("(?<![\\d.,/-])([1-9])[\\t ]*/[\\t ]*(one|two|three|four|five|six|[1-9])(?![\\d.,])|(?<![\\p{L}\\d])(one|two|three|four|five|six)[\\t ]*\\([\\t ]*([1-9])[\\t ]*\\)|(?<![\\d.,/-])([1-9])[\\t ]*\\([\\t ]*(one|two|three|four|five|six)[\\t ]*\\)",Pattern.CASE_INSENSITIVE);
 private static int number(String token){String t=token.toLowerCase(Locale.ROOT);return WORDS.containsKey(t)?WORDS.get(t):Integer.parseInt(t);}

 public static OptionalInt detect(String text){
  if(text==null||text.isBlank())return OptionalInt.empty();
  String[] lines=text.substring(0,Math.min(text.length(),100_000)).split("\\R");
  var values=new TreeSet<Integer>();
  for(int i=0;i<lines.length;i++){
   var label=LABEL.matcher(lines[i]);
   while(label.find()){
    String rest=lines[i].substring(label.end());
    var same=SAME_LINE.matcher(rest);
    if(same.find()){
     var both=DIGIT_WORD.matcher(rest);
     values.add(both.find()?agreed(both):number(same.group(1)));
     continue;
    }
    // table form: the value stands in one of the next two non-empty lines, stated as "3 / THREE", "THREE (3)" or "3 (THREE)"
    int seen=0;
    for(int j=i+1;j<lines.length&&seen<2;j++){
     if(lines[j].isBlank())continue;seen++;
     var both=DIGIT_WORD.matcher(lines[j]);
     if(both.find()){int n=agreed(both);if(n>0){values.add(n);break;}}
    }
   }
  }
  values.remove(0);
  return values.size()==1?OptionalInt.of(values.first()):OptionalInt.empty();
 }
 /** The number when digit and word of the match agree (3 / THREE, THREE (3), 3 / 3), else 0. */
 private static int agreed(Matcher m){
  String a,b;
  if(m.group(1)!=null){a=m.group(1);b=m.group(2);}else if(m.group(3)!=null){a=m.group(4);b=m.group(3);}else{a=m.group(5);b=m.group(6);}
  return number(a)==number(b)?number(a):0;
 }
}
