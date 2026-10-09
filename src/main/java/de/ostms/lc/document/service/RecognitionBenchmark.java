package de.ostms.lc.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.domain.DocumentType;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

/** Public synthetic regression corpus only: never customer documents or tenant training examples. */
@Service
public class RecognitionBenchmark {
 public record Expected(int fromPage,int toPage,DocumentType type,String date,String reference,Integer copy){}
 public record Example(String id,List<String> pages,List<Expected> expected){}
 public record Corpus(String version,List<Example> examples){}
 public record Counts(int positiveLabels,int correct,int missed,int falsePositives,int negativeLabels,int correctNegatives){}
 public record Boundaries(int correct,int proposed,int expected,Double precision,Double recall){}
 public record CaseResult(String id,boolean exactSplit,int correctTypes,int pages){}
 public record Report(String corpusVersion,String corpusSha256,String engineSha256,String scope,int examples,int pages,int correctTypes,
                      Map<String,Counts> types,Boundaries boundaries,Map<String,Counts> metadata,List<CaseResult> cases){}
 private final ObjectMapper json;
 private Report cached;
 public RecognitionBenchmark(ObjectMapper json){this.json=json;}
 public synchronized Report run()throws Exception{
  if(cached!=null)return cached;
  byte[] bytes;try(var input=getClass().getResourceAsStream("/recognition-benchmark-v1.json")){if(input==null)throw new IllegalStateException("Benchmark corpus unavailable");bytes=input.readAllBytes();}
  var corpus=json.readValue(bytes,Corpus.class);
  cached=evaluate(corpus,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));return cached;
 }
 static final class Counter{
  int positive,correct,missed,falsePositive,negative,correctNegative;
  void add(Object expected,Object actual){if(expected!=null){positive++;if(expected.equals(actual))correct++;else{missed++;if(actual!=null)falsePositive++;}}else{negative++;if(actual==null)correctNegative++;else falsePositive++;}}
  Counts view(){return new Counts(positive,correct,missed,falsePositive,negative,correctNegative);}
 }
 static Report evaluate(Corpus corpus,String digest)throws Exception{
  var types=new TreeMap<String,Counter>();var metadata=new LinkedHashMap<String,Counter>();
  for(String key:List.of("documentDate","lcReference","originalCopy"))metadata.put(key,new Counter());
  var cases=new ArrayList<CaseResult>();int pages=0,correctTypes=0,expectedBoundaries=0,proposedBoundaries=0,correctBoundaries=0;
  for(var example:corpus.examples()){
   var labels=example.expected().stream().map(e->new PdfDocumentSplitter.Part(e.fromPage(),e.toPage(),e.type())).toList();
   PdfDocumentSplitter.validate(labels,example.pages().size());
   var proposal=PdfDocumentSplitter.propose(pdf(example.pages()),null);
   int correct=0;
   for(int number=1;number<=proposal.pageCount();number++){
    final int page=number;
    var expected=labels.stream().filter(p->p.fromPage()<=page&&p.toPage()>=page).findFirst().orElseThrow().documentType();
    var actual=proposal.parts().stream().filter(p->p.fromPage()<=page&&p.toPage()>=page).findFirst().orElseThrow().documentType();
    if(expected==actual)correct++;
    for(var type:DocumentType.values())types.computeIfAbsent(type.name(),key->new Counter()).add(expected==type?type:null,actual==type?type:null);
   }
   var before=boundaries(labels);var after=boundaries(proposal.parts());
   expectedBoundaries+=before.size();proposedBoundaries+=after.size();after.retainAll(before);correctBoundaries+=after.size();
   // Metadata is measured on labelled document spans, separately from split quality.
   for(var expected:example.expected()){
    String text=String.join("\n",example.pages().subList(expected.fromPage()-1,expected.toPage()));
    metadata.get("documentDate").add(expected.date()==null?null:LocalDate.parse(expected.date()),DocumentDateDetector.detect(text).date());
    metadata.get("lcReference").add(expected.reference(),DocumentReferenceDetector.detect(text));
    metadata.get("originalCopy").add(expected.copy(),DocumentCopyDetector.detect(text).copyNumber());
   }
   pages+=proposal.pageCount();correctTypes+=correct;cases.add(new CaseResult(example.id(),proposal.parts().stream().map(p->new PdfDocumentSplitter.Part(p.fromPage(),p.toPage(),p.documentType())).toList().equals(labels),correct,proposal.pageCount()));
  }
  var perType=new TreeMap<String,Counts>();types.forEach((key,value)->{if(value.positive>0||value.falsePositive>0)perType.put(key,value.view());});
  var fields=new LinkedHashMap<String,Counts>();metadata.forEach((key,value)->fields.put(key,value.view()));
  return new Report(corpus.version(),digest,engineDigest(),"SYNTHETIC_TEXT_ONLY_NOT_REAL_SCAN_ACCURACY",cases.size(),pages,correctTypes,Collections.unmodifiableMap(perType),
   new Boundaries(correctBoundaries,proposedBoundaries,expectedBoundaries,proposedBoundaries==0?null:(double)correctBoundaries/proposedBoundaries,expectedBoundaries==0?null:(double)correctBoundaries/expectedBoundaries),Collections.unmodifiableMap(fields),List.copyOf(cases));
 }
 private static String engineDigest()throws Exception{
  var hash=java.security.MessageDigest.getInstance("SHA-256");
  for(Class<?> type:List.of(DocumentClassifier.class,PdfDocumentSplitter.class,DocumentDateDetector.class,DocumentReferenceDetector.class,DocumentCopyDetector.class)){
   try(var input=type.getResourceAsStream(type.getSimpleName()+".class")){if(input==null)throw new IllegalStateException("Engine fingerprint unavailable");hash.update(input.readAllBytes());}
  }
  return HexFormat.of().formatHex(hash.digest());
 }
 private static Set<Integer> boundaries(List<PdfDocumentSplitter.Part> parts){var result=new HashSet<Integer>();for(int i=0;i<parts.size()-1;i++)result.add(parts.get(i).toPage());return result;}
 private static byte[] pdf(List<String> pages)throws Exception{
  try(var document=new PDDocument();var output=new ByteArrayOutputStream()){
   for(String text:pages){var page=new PDPage();document.addPage(page);try(var stream=new PDPageContentStream(document,page)){stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),10);stream.newLineAtOffset(30,740);for(String line:text.split("\\R")){stream.showText(line);stream.newLineAtOffset(0,-14);}stream.endText();}}
   document.save(output);return output.toByteArray();
  }
 }
}
