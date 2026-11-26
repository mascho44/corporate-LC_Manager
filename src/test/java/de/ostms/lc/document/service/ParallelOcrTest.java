package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Gescannte Seiten werden gleichzeitig erkannt, das Ergebnis bleibt in Seitenreihenfolge, ein Zeitfehler trifft nur die eine Seite. */
class ParallelOcrTest {
 static class FakeOcr extends DocumentExtractionService {
  final AtomicInteger running=new AtomicInteger(),peak=new AtomicInteger();
  volatile int timeoutPage=-1,densePage=-1;
  final java.util.Set<Integer> fullRuns=java.util.concurrent.ConcurrentHashMap.newKeySet();
  @Override void runOcrStep(ProcessBuilder builder,long seconds)throws java.io.IOException,InterruptedException{
   var args=builder.command();
   if(args.get(0).equals("pdftoppm")){
    int pageNo=Integer.parseInt(args.get(args.indexOf("-f")+1));
    var picture=new java.awt.image.BufferedImage(1000,1000,java.awt.image.BufferedImage.TYPE_INT_RGB);
    var g=picture.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,1000,1000);g.setColor(java.awt.Color.BLACK);
    if("120".equals(args.get(args.indexOf("-r")+1))&&pageNo==densePage)for(int y=0;y<1000;y+=6)g.fillRect(0,y,1000,3);
    else g.fillRect(0,0,1000,3);
    g.dispose();
    javax.imageio.ImageIO.write(picture,"png",java.nio.file.Path.of(args.get(args.size()-1)+"-"+pageNo+".png").toFile());
    return;
   }
   int page=Integer.parseInt(java.nio.file.Path.of(args.get(1)).getFileName().toString().replaceAll("[^0-9]",""));
   int now=running.incrementAndGet();peak.accumulateAndGet(now,Math::max);
   try{
    Thread.sleep((6-page)*80L); // frühe Seiten sind langsamer: fertig werden die späten zuerst
    if(page==timeoutPage)throw new BoundedProcess.TimeoutException("OCR");
    boolean quick=java.nio.file.Path.of(args.get(1)).getFileName().toString().startsWith("quick");
    if(java.nio.file.Path.of(args.get(1)).getFileName().toString().startsWith("page"))fullRuns.add(page);
    int count=1;
    java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".txt"),"Seite "+page);
    java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".tsv"),("5\t1\t1\t1\t1\t1\t400\t800\t200\t40\t99\tSeite"+page+"\n").repeat(count));
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
 @Test void progressFollowsCompletionNotPageOrder()throws Exception{
  var service=new FakeOcr();ReflectionTestUtils.setField(service,"ocrParallelism",5);
  var doc=document(5);var snapshots=new java.util.ArrayList<String>();
  service.extractInBackground(doc,d->snapshots.add(d.getOcrEvidenceJson()));
  assertThat(snapshots).hasSize(5);
  var first=DocumentExtractionService.readEvidence(snapshots.get(0));
  assertThat(first.pages().get(0).status()).as("die langsame Seite 1 ist noch offen").isEqualTo("QUEUED");
  assertThat(first.pages().stream().filter(p->"OCR_EXTRACTED".equals(p.status())).count()).as("fertig ist schon eine spätere Seite").isEqualTo(1);
  for(int i=0;i<5;i++){
   var snapshot=DocumentExtractionService.readEvidence(snapshots.get(i));
   assertThat(snapshot.pages().stream().filter(p->"OCR_EXTRACTED".equals(p.status())).count()).as("Fortschritt nach "+(i+1)+" Seiten").isEqualTo(i+1);
  }
 }
 @Test void aTimeoutOnOnePageLeavesTheOthersIntact()throws Exception{
  var service=new FakeOcr();ReflectionTestUtils.setField(service,"ocrParallelism",4);service.timeoutPage=3;
  var doc=document(5);service.extractInBackground(doc);
  var evidence=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  assertThat(evidence.pages()).extracting(p->p.status()).containsExactly("OCR_EXTRACTED","OCR_EXTRACTED","OCR_TIMEOUT","OCR_EXTRACTED","OCR_EXTRACTED");
  assertThat(doc.getExtractedText()).contains("Seite 2","Seite 4").doesNotContain("Seite 3");
  assertThat(doc.getExtractionStatus()).isEqualTo("OCR_PARTIAL");
 }
 @Test void aVeryDensePageIsNotRecognisedInFull()throws Exception{
  var service=new FakeOcr();ReflectionTestUtils.setField(service,"ocrParallelism",4);ReflectionTestUtils.setField(service,"denseSkipLines",130);service.densePage=2;
  var doc=document(3);service.extractInBackground(doc);
  assertThat(service.fullRuns).containsExactlyInAnyOrder(1,3);
  var evidence=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  assertThat(evidence.pages()).extracting(p->p.attempts()).containsExactly(2,0,2);
  assertThat(evidence.pages()).extracting(p->p.status()).containsOnly("OCR_EXTRACTED");
  assertThat(doc.getExtractionStatus()).isEqualTo("OCR_EXTRACTED");
 }
 @Test void theQuickCheckCanBeSwitchedOff()throws Exception{
  var service=new FakeOcr();ReflectionTestUtils.setField(service,"denseSkipLines",0);service.densePage=2;
  var doc=document(3);service.extractInBackground(doc);
  assertThat(service.fullRuns).containsExactlyInAnyOrder(1,2,3);
 }
}
