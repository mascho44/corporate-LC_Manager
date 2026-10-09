package de.ostms.lc.document.service;

import java.util.*;
/** Coordinates are pixels in the 200-DPI rendered page, not PDF points. */
public record OcrEvidence(String engineVersion,String method,int dpi,double threshold,List<Word> words,List<PageResult> pages) {
 public OcrEvidence(String engineVersion,String method,int dpi,double threshold,List<Word> words){this(engineVersion,method,dpi,threshold,words,List.of());}
 public OcrEvidence {words=words==null?List.of():List.copyOf(words);pages=pages==null?List.of():List.copyOf(pages);}
 public record PageResult(int page,String status,int attempts){}
 /** Reuse only explicitly completed OCR pages. Never treat partial word output as complete. */
 public String completedPageText(int page){
  if(pages.stream().noneMatch(p->p.page()==page&&"OCR_EXTRACTED".equals(p.status())))return null;
  StringBuilder text=new StringBuilder();int top=-1;
  for(var word:words)if(word.page()==page){text.append(top>=0&&Math.abs(word.top()-top)>8?'\n':' ');text.append(word.text());top=word.top();}
  return text.toString();
 }
 public record Word(String text,Double confidence,int page,int left,int top,int width,int height){}
 public record Assessment(Double score,Double meanScore,String status,String method,String engineVersion,
                          double threshold,String originalValue,List<Word> words){}
 public static List<Word> parseTsv(String tsv,int page){
  List<Word> words=new ArrayList<>();
  for(String line:tsv.split("\\R")){
   String[] cells=line.split("\t",12);if(cells.length<12||!"5".equals(cells[0])||cells[11].isBlank())continue;
   try{double conf=Double.parseDouble(cells[10]);Double score=Double.isFinite(conf)&&conf>=0&&conf<=100?conf/100:null;
    int left=Integer.parseInt(cells[6]),top=Integer.parseInt(cells[7]),width=Integer.parseInt(cells[8]),height=Integer.parseInt(cells[9]);
    if(left<0||top<0||width<0||height<0)continue;
    words.add(new Word(cells[11],score,page,left,top,width,height));
   }catch(NumberFormatException ignored){}
  }return List.copyOf(words);
 }
 public Assessment assess(String value,double threshold){
  String needle=compact(value);if(needle.isEmpty())return unavailable(value,threshold);
  List<Word> match=null;
  // Exact full-token sequence only. Repeated or transformed values are deliberately unknown.
  for(int start=0;start<words.size();start++){
   if(valueToken(words.get(start).text()).isEmpty())continue;
   StringBuilder candidate=new StringBuilder();
   for(int end=start;end<words.size()&&candidate.length()<=needle.length();end++){
    if(end>start&&compact(words.get(end).text()).matches("^:\\d{2}[A-Z]?:.*"))break;
    candidate.append(valueToken(words.get(end).text()));
    if(candidate.toString().equals(needle)){
     if(match!=null)return unavailable(value,threshold);
     match=words.subList(start,end+1);break;
    }
    if(!needle.startsWith(candidate.toString()))break;
   }
  }
  if(match==null||match.stream().anyMatch(w->w.confidence()==null))return unavailable(value,threshold);
  double min=match.stream().mapToDouble(Word::confidence).min().orElseThrow();
  double mean=match.stream().mapToDouble(Word::confidence).average().orElseThrow();
  return new Assessment(min,mean,min<threshold?"REVIEW":"MEASURED",method,engineVersion,threshold,value,List.copyOf(match));
 }
 private Assessment unavailable(String value,double threshold){return new Assessment(null,null,"UNAVAILABLE",method,engineVersion,threshold,value,List.of());}
 private static String compact(String value){return value==null?"":value.replaceAll("\\s+","");}
 private static String valueToken(String value){return compact(value).replaceFirst("^:\\d{2}[A-Z]?:","");}
}
