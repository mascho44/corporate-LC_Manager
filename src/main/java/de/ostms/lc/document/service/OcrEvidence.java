package de.ostms.lc.document.service;

import java.util.*;
/** Coordinates are pixels in the 200-DPI rendered page, not PDF points. */
public record OcrEvidence(String engineVersion,String method,int dpi,double threshold,List<Word> words,List<PageResult> pages) {
 public OcrEvidence(String engineVersion,String method,int dpi,double threshold,List<Word> words){this(engineVersion,method,dpi,threshold,words,List.of());}
 public OcrEvidence {words=words==null?List.of():List.copyOf(words);pages=pages==null?List.of():List.copyOf(pages);}
 public record PageResult(int page,String status,int attempts,double correctionDegrees,String recognizedText){
  public PageResult(int page,String status,int attempts){this(page,status,attempts,0,null);}
 }
 /** Reuse only explicitly completed OCR pages. Never treat partial word output as complete. */
 public String completedPageText(int page){
  var completed=pages.stream().filter(p->p.page()==page&&"OCR_EXTRACTED".equals(p.status())).findFirst().orElse(null);
  if(completed==null)return null;
  if(completed.recognizedText()!=null)return completed.recognizedText();
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
 /** Assesses all values of one message in document order: each search starts behind the previous field, so repeated values are no longer ambiguous and lines the normaliser dropped no longer break the match. */
 public List<Assessment> assessAll(List<String> values,double threshold){
  if("PDF_TEXT_POSITIONS".equals(method)){// digital text layer: nothing was recognised, so there is no OCR quality to measure or to doubt
   return values.stream().map(v->new Assessment(null,null,"NOT_APPLICABLE",method,engineVersion,threshold,v,List.<Word>of())).toList();
  }
  var out=new ArrayList<Assessment>(values.size());int cursor=0;
  for(String value:values){
   var found=locate(value,threshold,cursor);
   if(found==null){out.add(assess(value,threshold));continue;}
   out.add(found.assessment());cursor=found.end();
  }
  return out;
 }
 private record Located(Assessment assessment,int end){}
 private static String token(String value){return value==null?"":value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
 private Located locate(String value,double threshold,int from){
  List<String> needle=new ArrayList<>();
  for(String part:Objects.toString(value,"").split("\\s+")){String t=token(part);if(!t.isEmpty())needle.add(t);}
  if(needle.isEmpty())return null;
  int bestStart=-1,bestEnd=-1,bestMatched=0;List<Integer> bestIdx=null;
  for(int start=from;start<words.size();start++){
   if(!token(valueToken(words.get(start).text())).equals(needle.get(0))&&!token(words.get(start).text()).equals(needle.get(0)))continue;
   List<Integer> idx=new ArrayList<>();int k=0,gap=0,j=start;
   for(;j<words.size()&&k<needle.size();j++){
    String raw=compact(words.get(j).text());
    if(j>start&&raw.matches("^:\\d{2}[A-Z]?:.*"))break;
    String t=token(valueToken(words.get(j).text()));if(t.isEmpty()){continue;}
    if(t.equals(needle.get(k))||token(words.get(j).text()).equals(needle.get(k))){idx.add(j);k++;gap=0;}
    else if(++gap>3)break;
   }
   if(k>bestMatched){bestMatched=k;bestStart=start;bestEnd=j;bestIdx=idx;}
   if(k==needle.size())break;
  }
  if(bestIdx==null||bestMatched<Math.max(1,(int)Math.ceil(needle.size()*0.85))||(bestMatched<2&&needle.get(0).length()<4))return null;
  var match=bestIdx.stream().map(words::get).toList();
  if(match.stream().anyMatch(w->w.confidence()==null))return new Located(unavailable(value,threshold),bestEnd);
  double min=match.stream().mapToDouble(Word::confidence).min().orElseThrow(),mean=match.stream().mapToDouble(Word::confidence).average().orElseThrow();
  return new Located(new Assessment(min,mean,min<threshold?"REVIEW":"MEASURED",method,engineVersion,threshold,value,List.copyOf(match)),bestEnd);
 }
 private Assessment unavailable(String value,double threshold){return new Assessment(null,null,"UNAVAILABLE",method,engineVersion,threshold,value,List.of());}
 private static String compact(String value){return value==null?"":value.replaceAll("\\s+","");}
 private static String valueToken(String value){return compact(value).replaceFirst("^:\\d{2}[A-Z]?:","");}
}
