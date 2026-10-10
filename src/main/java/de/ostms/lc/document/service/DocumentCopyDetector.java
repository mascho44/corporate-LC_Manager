package de.ostms.lc.document.service;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative text/OCR stamp hints, never proof that a document is an original. */
public final class DocumentCopyDetector {
 private static final String ORIGINAL="[o0]r[il1]g[il1]n[a4][il1]";
 private static final String KIND="("+ORIGINAL+"|originale|copy|kop[il1t]+e|copie|copia|abschrift|durchschrift|duplicate|duplikat)";
 private static final String OF="(?:[\\t ]*+(?:/|of|von)[\\t ]*+[1-9])?";
 private static final Pattern MARK=Pattern.compile("(?i)^(?:([1-3])(?:st|nd|rd)?"+OF+"[\\t ]++)?"+KIND+"(?:[\\t ]++(?:no\\.?[\\t ]*+)?([1-3])"+OF+")?[\\t ]*+$");
 private static final Pattern NEGOTIABLE=Pattern.compile("(?i)[\\t ]*+[-–/,]?[\\t ]*+(?:non|not)[\\t -]*+negotiable[\\t ]*+");
 private static final Pattern SEGMENT=Pattern.compile("[\\t ]+[-–—/|:][\\t ]+|[()\\[\\]|]");
 private static final Pattern EDGE=Pattern.compile("^[\\s*_=~\"'.:;|<>#+-]+|[\\s*_=~\"'.:;|<>#+-]+$");
 private static final Pattern SPACED_RUN=Pattern.compile("(?<!\\S)(?:\\p{L} ){5,}\\p{L}(?!\\S)");
 private static final Pattern WORD_KIND=Pattern.compile("(?i)^"+KIND+"$");
 private static final Pattern SPACED=Pattern.compile("^(?:\\p{L} ){3,}\\p{L}$");
 /** True for a single word that names a copy kind (ORIGINAL, COPY, KOPIE, ...); used to locate a stamp on a page. */
 public static boolean isKindWord(String word){return word!=null&&WORD_KIND.matcher(clean(word)).matches();}
 public record Hint(String kind,Integer copyNumber,String evidence){}
 private DocumentCopyDetector(){}
 private static String ordinals(String stamp){return stamp.replaceFirst("(?i)^first[\\t ]++","1 ").replaceFirst("(?i)^second[\\t ]++","2 ").replaceFirst("(?i)^third[\\t ]++","3 ");}
 private static String clean(String value){return EDGE.matcher(value).replaceAll("").trim();}
 public static Hint detect(String text){
  if(text==null||text.isBlank())return new Hint("UNKNOWN",null,"");
  Set<String> kinds=new HashSet<>();Set<Integer> numbers=new HashSet<>();
  String bounded=text.substring(0,Math.min(text.length(),100_000));
  int nonBlank=0;
  for(String rawLine:bounded.split("\\R")){
   if(rawLine.length()>100)continue;
   String line=SPACED_RUN.matcher(rawLine).replaceAll(match->match.group().replace(" ",""));
   if(!line.isBlank())nonBlank++;
   var candidates=new ArrayList<String>();candidates.add(line);
   // A stamp beside a logo or letterhead shares its OCR line: in the page head, short lines may carry it as a single word.
   if(nonBlank<=15&&!MARK.matcher(ordinals(clean(NEGOTIABLE.matcher(line.trim()).replaceAll(" ")))).matches()){
    String[] words=line.trim().split("[\\s]+");
    if(words.length>=2&&words.length<=4)for(int w=0;w<words.length;w++){
     String word=clean(words[w]);
     if(WORD_KIND.matcher(word).matches()&&word.equals(word.toUpperCase(Locale.ROOT)))candidates.add(w+1<words.length&&words[w+1].matches("[1-3]")?word+" "+words[w+1]:word);
    }
   }
   if(SEGMENT.matcher(line).find())candidates.addAll(Arrays.asList(SEGMENT.split(line)));
   for(String candidate:candidates){
    String stamp=clean(candidate);if(stamp.isEmpty()||stamp.length()>60)continue;
    stamp=clean(NEGOTIABLE.matcher(stamp).replaceAll(" "));
    if(SPACED.matcher(stamp).matches())stamp=stamp.replace(" ","");
    stamp=ordinals(stamp);
    var words=stamp.split("[\\t ]+".replace("[\\t ]+"," +"));
    if(words.length>=2&&words.length<=4&&Arrays.stream(words).allMatch(w->WORD_KIND.matcher(w).matches())){
     boolean firstOriginal=words[0].toLowerCase(Locale.ROOT).matches(ORIGINAL+"|originale");
     if(Arrays.stream(words).allMatch(w->w.toLowerCase(Locale.ROOT).matches(ORIGINAL+"|originale")==firstOriginal))stamp=words[0]; // "KOPIE COPY": the same word in several languages
    }
    var matcher=MARK.matcher(stamp);if(!matcher.matches())continue;
    boolean original=matcher.group(2).toLowerCase(Locale.ROOT).matches(ORIGINAL+"|originale");
    kinds.add(original?"ORIGINAL":"COPY");
    numbers.add(original?0:1); // only Original or Copy; the application counts documents
   }
  }
  if(kinds.size()>1)return new Hint("CONFLICT",null,"Widersprüchliche Original-/Copy-Kennzeichnungen – bitte prüfen.");
  if(kinds.isEmpty())return new Hint("UNKNOWN",null,"");
  String kind=kinds.iterator().next();Integer number=numbers.iterator().next();
  return new Hint(kind,number,(kind.equals("ORIGINAL")?"Original":"Copy")+" als Text-/OCR-Kennzeichnung erkannt – bitte prüfen; kein Echtheitsnachweis.");
 }
}
