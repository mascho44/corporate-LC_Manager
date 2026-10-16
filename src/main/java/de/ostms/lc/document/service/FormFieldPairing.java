package de.ostms.lc.document.service;

import java.util.*;

/**
 * Scanned forms print the caption of a field above its value, several fields side by side ("Invoice No.: | Invoice Date: | Credit No." over
 * "INV-1 | 05.12.2026 | TEST-LC-1"). Read line by line the captions end up apart from their values. From the word positions this pairs them
 * again by column and returns "Caption: value" lines, which are appended to the recognised text (the original text stays unchanged).
 */
public final class FormFieldPairing {
 private FormFieldPairing(){}
 static final int MAX_LABEL_WORDS=6,MAX_VALUE_LINES=3;
 private record Line(int page,double top,double height,List<OcrEvidence.Word> words){}
 private record Label(String text,int left){}

 /** "Caption: value" lines for all caption rows found; empty when none. */
 public static List<String> pairs(List<OcrEvidence.Word> words){
  if(words==null||words.isEmpty())return List.of();
  var lines=lines(words);var out=new ArrayList<String>();
  for(int i=0;i<lines.size();i++){
   var labels=labelRow(lines.get(i));if(labels.isEmpty())continue;
   var row=lines.get(i);int used=0;List<Line> below=new ArrayList<>();
   for(int j=i+1;j<lines.size()&&used<MAX_VALUE_LINES;j++){
    var next=lines.get(j);if(next.page()!=row.page()||next.top()-row.top()>row.height()*3.2*(used+1))break;
    if(!labelRow(next).isEmpty())break;
    below.add(next);used++;
   }
   double tol=Math.max(8,row.height()*0.6);
   for(int c=0;c<labels.size();c++){
    int from=labels.get(c).left(),to=c+1<labels.size()?labels.get(c+1).left():Integer.MAX_VALUE;
    var parts=new ArrayList<String>();
    for(var line:below){
     var text=new StringJoiner(" ");
     for(var w:line.words())if(w.left()>=from-tol&&w.left()<to-tol)text.add(w.text());
     if(text.length()>0)parts.add(text.toString());
    }
    if(!parts.isEmpty())out.add(labels.get(c).text()+" "+String.join(", ",parts));
   }
  }
  return List.copyOf(out);
 }
 /** Words of one page grouped into lines (similar vertical centre), words left to right, lines top to bottom. */
 private static List<Line> lines(List<OcrEvidence.Word> words){
  var sorted=new ArrayList<>(words);sorted.removeIf(w->w.text()==null||w.text().isBlank());
  sorted.sort(Comparator.comparingInt(OcrEvidence.Word::page).thenComparingDouble(w->w.top()+w.height()/2.0));
  var lines=new ArrayList<Line>();List<OcrEvidence.Word> current=new ArrayList<>();double centre=0;int page=-1;
  for(var w:sorted){
   double c=w.top()+w.height()/2.0;
   if(!current.isEmpty()&&(w.page()!=page||Math.abs(c-centre)>Math.max(5,current.get(0).height()*0.55))){lines.add(build(page,current));current=new ArrayList<>();}
   if(current.isEmpty()){centre=c;page=w.page();}current.add(w);
  }
  if(!current.isEmpty())lines.add(build(page,current));
  return lines;
 }
 private static Line build(int page,List<OcrEvidence.Word> words){
  var sorted=new ArrayList<>(words);sorted.sort(Comparator.comparingInt(OcrEvidence.Word::left));
  double top=sorted.stream().mapToInt(OcrEvidence.Word::top).average().orElse(0),height=sorted.stream().mapToInt(OcrEvidence.Word::height).max().orElse(10);
  return new Line(page,top,height,sorted);
 }
 /** The captions of a row consisting only of captions ("A: B: C:"), each of at most a few words ending with a colon; empty otherwise. */
 private static List<Label> labelRow(Line line){
  var labels=new ArrayList<Label>();var text=new ArrayList<String>();int left=-1;
  for(var w:line.words()){
   if(text.isEmpty())left=w.left();text.add(w.text());
   if(w.text().endsWith(":")){
    if(text.size()>MAX_LABEL_WORDS||text.get(0).length()<2)return List.of();
    labels.add(new Label(String.join(" ",text),left));text=new ArrayList<>();
   }
  }
  return text.isEmpty()&&!labels.isEmpty()?labels:List.of();
 }
}
