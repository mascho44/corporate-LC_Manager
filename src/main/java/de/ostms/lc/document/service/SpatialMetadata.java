package de.ostms.lc.document.service;
import java.util.*;

/** Position-based row reconstruction; suggestions remain human-reviewable, not confidence percentages. */
public final class SpatialMetadata {
 public record Field(String value,int page,int left,int top,int width,int height,String evidence,String status){}
 private static final Map<String,List<String>> LABELS=Map.of(
  "reference",List.of("letter of credit number","lc reference","l/c no.","lc no.","akkreditivnummer"),
  "documentNumber",List.of("invoice number","invoice no.","document number","rechnungsnummer"),
  "documentDate",List.of("invoice date","document date","date of issue","issue date","rechnungsdatum","ausstellungsdatum"),
  "amount",List.of("invoice amount","grand total","amount due","total"),
  "currency",List.of("currency","währung"));
 public static Map<String,Field> detect(OcrEvidence evidence){
  if(evidence==null||evidence.words()==null||evidence.words().size()>100_000)return Map.of();
  var words=evidence.words().stream().sorted(Comparator.comparingInt(OcrEvidence.Word::page).thenComparingInt(OcrEvidence.Word::top).thenComparingInt(OcrEvidence.Word::left)).toList();
  var rows=new ArrayList<List<OcrEvidence.Word>>();
  for(var word:words){if(rows.isEmpty()){rows.add(new ArrayList<>());}var row=rows.get(rows.size()-1);if(!row.isEmpty()&&(row.get(0).page()!=word.page()||Math.abs(row.get(0).top()-word.top())>Math.max(3,row.get(0).height()/2))){row=new ArrayList<>();rows.add(row);}row.add(word);}
  var candidates=new HashMap<String,List<Field>>();
  for(var row:rows){row.sort(Comparator.comparingInt(OcrEvidence.Word::left));
   for(int start=0;start<row.size();start++)for(var entry:LABELS.entrySet())for(String label:entry.getValue()){
    StringBuilder prefix=new StringBuilder();int end=start;
    for(;end<row.size()&&end<start+6;end++){if(prefix.length()>0)prefix.append(' ');prefix.append(row.get(end).text().toLowerCase(Locale.ROOT).replace(":",""));if(prefix.toString().equals(label))break;if(!label.startsWith(prefix.toString()))break;}
    if(!prefix.toString().equals(label)||end+1>=row.size())continue;
    int first=end+1;if(row.get(first).text().equals(":"))first++;if(first>=row.size())continue;
    if(row.get(first).left()-(row.get(end).left()+row.get(end).width())>Math.max(row.get(first).height(),row.get(end).height())*6)continue;
    var values=new ArrayList<OcrEvidence.Word>();StringBuilder raw=new StringBuilder();
    for(int i=first;i<row.size()&&i<first+5;i++){if(i>first&&row.get(i).left()-(row.get(i-1).left()+row.get(i-1).width())>row.get(i).height()*3)break;if(raw.length()>0)raw.append(' ');raw.append(row.get(i).text());values.add(row.get(i));Object parsed=MetadataFieldAnchors.parse(entry.getKey(),raw.toString());if(parsed!=null){int left=values.get(0).left(),top=values.stream().mapToInt(OcrEvidence.Word::top).min().orElseThrow(),right=values.stream().mapToInt(w->w.left()+w.width()).max().orElseThrow(),bottom=values.stream().mapToInt(w->w.top()+w.height()).max().orElseThrow();candidates.computeIfAbsent(entry.getKey(),key->new ArrayList<>()).add(new Field(parsed.toString(),row.get(0).page(),left,top,right-left,bottom-top,label+": "+raw,"POSITION_REVIEW"));break;}}
   }
  }
  var result=new LinkedHashMap<String,Field>();candidates.forEach((key,values)->{var locations=new LinkedHashMap<String,Field>();values.forEach(value->locations.put(value.page()+":"+value.left()+":"+value.top()+":"+value.value(),value));if(locations.size()==1)result.put(key,locations.values().iterator().next());});return result;
 }
}
