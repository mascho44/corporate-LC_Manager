package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Gescannte Seiten werden gleichzeitig erkannt, das Ergebnis bleibt in Seitenreihenfolge, ein Zeitfehler trifft nur die eine Seite. */
class ParallelOcrTest {
 static class FakeOcr extends DocumentExtractionService {
  final AtomicInteger running=new AtomicInteger(),peak=new AtomicInteger();
  volatile int timeoutPage=-1;
  @Override void runOcrStep(ProcessBuilder builder,long seconds)throws java.io.IOException,InterruptedException{
   var args=builder.command();
   if(args.get(0).equals("pdftoppm")){
    java.nio.file.Files.write(java.nio.file.Path.of(args.get(args.size()-1)+"-"+args.get(args.indexOf("-f")+1)+".png"),new byte[]{1});
    return;
   }
   int page=Integer.parseInt(java.nio.file.Path.of(args.get(1)).getFileName().toString().replaceAll("[^0-9]",""));
   int now=running.incrementAndGet();peak.accumulateAndGet(now,Math::max);
   try{
    Thread.sleep((6-page)*80L); // frühe Seiten sind langsamer: fertig werden die späten zuerst
    if(page==timeoutPage)throw new BoundedProcess.TimeoutException("OCR");
    java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".txt"),"Seite "+page);
    java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".tsv"),"5\t1\t1\t1\t1\t1\t400\t800\t200\t40\t99\tSeite"+page+"\n");
   }finally{running.decrementAndGet();}
  }
 }
 private static de.ostms.lc.document.domain.LcDocument document(int pages)throws Exception{
  var doc=new de.ostms.lc.document.domain.LcDocument();doc.setContentType("application/pdf");doc.setOriginalFilename("scan.pdf");
  doc.setContent(PdfDocumentSplitterTest.pdf(java.util.Collections.nCopies(pages,"").toArray(new String[0])));return doc;
 }

 @Test void pagesAreRecognisedAtTheSameTimeAndMergedInOrder()throws Exception{
  var service=new FakeOcr();ReflectionTestUtils.setField(service,"ocrParallelism",4);
  var doc=document(5);service.extractInBackground(doc);
  assertThat(service.peak.get()).as("gleichzeitige Läufe").isGreaterThan(1);
  assertThat(doc.getExtractionStatus()).isEqualTo("OCR_EXTRACTED");
  assertThat(doc.getExtractedText()).containsSubsequence("Seite 1","Seite 2","Seite 3","Seite 4","Seite 5");
  var evidence=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  assertThat(evidence.pages()).extracting(p->p.page()).containsExactly(1,2,3,4,5);
  assertThat(evidence.pages()).extracting(p->p.status()).containsOnly("OCR_EXTRACTED");
  assertThat(evidence.words()).extracting(w->w.page()).containsExactly(1,2,3,4,5);
 }
 @Test void aTimeoutOnOnePageLeavesTheOthersIntact()throws Exception{
  var service=new FakeOcr();ReflectionTestUtils.setField(service,"ocrParallelism",4);service.timeoutPage=3;
  var doc=document(5);service.extractInBackground(doc);
  var evidence=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  assertThat(evidence.pages()).extracting(p->p.status()).containsExactly("OCR_EXTRACTED","OCR_EXTRACTED","OCR_TIMEOUT","OCR_EXTRACTED","OCR_EXTRACTED");
  assertThat(doc.getExtractedText()).contains("Seite 2","Seite 4").doesNotContain("Seite 3");
  assertThat(doc.getExtractionStatus()).isEqualTo("OCR_PARTIAL");
 }
}
