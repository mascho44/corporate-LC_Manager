package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** Synthetic labelled holdout scenarios: no customer documents or licensed texts. */
class RecognitionRegressionTest {
 @Test void pageTimeoutRetainsOtherPagesAndRetryRunsOnlyMissingPage()throws Exception{
  class FakeOcr extends DocumentExtractionService{
   boolean fail=true;java.util.List<Integer> recognized=new java.util.ArrayList<>();
   @Override void runOcrStep(ProcessBuilder builder,long seconds)throws java.io.IOException{
    var args=builder.command();
    if(args.get(0).equals("pdftoppm")){
     int from=Integer.parseInt(args.get(args.indexOf("-f")+1)),to=Integer.parseInt(args.get(args.indexOf("-l")+1));
     for(int page=from;page<=to;page++)java.nio.file.Files.write(java.nio.file.Path.of(args.get(args.size()-1)+"-"+page+".png"),new byte[]{1});
    }else{
     int page=Integer.parseInt(java.nio.file.Path.of(args.get(1)).getFileName().toString().replaceAll("[^0-9]",""));
     recognized.add(page);if(page==2&&fail)throw new BoundedProcess.TimeoutException("synthetic");
     java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".txt"),"Synthetic page "+page);
     java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".tsv"),"5\t1\t1\t1\t1\t1\t10\t10\t50\t20\t99\tSynthetic page "+page);
    }
   }
  }
  var service=new FakeOcr();var doc=new de.ostms.lc.document.domain.LcDocument();doc.setContentType("application/pdf");doc.setOriginalFilename("synthetic.pdf");doc.setContent(PdfDocumentSplitterTest.pdf("","",""));
  var checkpoints=new java.util.ArrayList<String>();service.extractInBackground(doc,value->checkpoints.add(value.getOcrEvidenceJson()));
  assertThat(doc.getExtractionStatus()).isEqualTo("OCR_PARTIAL");assertThat(doc.getExtractedText()).contains("page 1","page 3");assertThat(checkpoints).hasSize(3);
  service.fail=false;service.recognized.clear();service.extractInBackground(doc);
  assertThat(doc.getExtractionStatus()).isEqualTo("OCR_EXTRACTED");assertThat(service.recognized).containsOnly(2);
  assertThat(doc.getExtractedText()).contains("page 1","page 2","page 3");
 }
 @Test void normalizedMetadataDoesNotInventSourceLocations(){
  var item=new de.ostms.lc.document.domain.DocumentInboxItem();item.setExtractedReference("SYN-REF");item.setExtractedText("Invoice date: 31.08.2026");
  var facts=RecognitionFacts.from(item);
  assertThat(facts).anyMatch(field->field.name().equals("documentDate")&&"2026-08-31".equals(field.value())&&field.status().equals("REVIEW_NO_EXACT_LOCATION"));
  assertThat(facts).allMatch(field->field.words().isEmpty());
 }
 @Test void fallbackNeverDropsWordsForAHighConfidenceButIncompleteAlternative(){
  var many=List.of(new OcrEvidence.Word("one",.4,1,0,0,1,1),new OcrEvidence.Word("two",.4,1,0,0,1,1));
  var shortHigh=List.of(new OcrEvidence.Word("one",.99,1,0,0,1,1));
  assertThat(OcrQualityPolicy.needsRetry(many)).isTrue();assertThat(OcrQualityPolicy.better(shortHigh,many)).isFalse();
 }
 @Test void resumeReusesOnlyCompletedPagesAndNeverOverwritesDigitalText(){
  var evidence=new OcrEvidence("synthetic","test",200,.8,List.of(
   new OcrEvidence.Word("Invoice",.9,1,10,10,20,10),
   new OcrEvidence.Word("Incomplete",.9,2,10,10,20,10)),
   List.of(new OcrEvidence.PageResult(1,"OCR_EXTRACTED",1),new OcrEvidence.PageResult(2,"OCR_TIMEOUT",1)));
  assertThat(DocumentExtractionService.resumePages(List.of("",""),evidence)).containsExactly(" Invoice","");
  assertThat(DocumentExtractionService.resumePages(List.of("Digital original",""),evidence)).containsExactly("Digital original","");
 }
 @Test void oldEvidenceRemainsReadableButDoesNotClaimCompletedPages()throws Exception{
  var old="{\"engineVersion\":\"test\",\"method\":\"test\",\"dpi\":200,\"threshold\":0.8,\"words\":[]}";
  var evidence=DocumentExtractionService.readEvidence(old);
  assertThat(evidence).isNotNull();assertThat(evidence.pages()).isEmpty();assertThat(evidence.completedPageText(1)).isNull();
 }
 @Test void sameDocumentReferenceLinksUnnumberedContinuationButDifferentReferenceSeparates()throws Exception{
  var proposal=PdfDocumentSplitter.propose(PdfDocumentSplitterTest.pdf(
   "COMMERCIAL INVOICE No. SYN-11", "Invoice No. SYN-11 continued details",
   "COMMERCIAL INVOICE No. SYN-12"),null);
  assertThat(proposal.parts()).containsExactly(new PdfDocumentSplitter.Part(1,2,DocumentType.COMMERCIAL_INVOICE),new PdfDocumentSplitter.Part(3,3,DocumentType.COMMERCIAL_INVOICE));
 }
 @Test void copyBoundaryWinsOverMatchingDocumentReference()throws Exception{
  var proposal=PdfDocumentSplitter.propose(PdfDocumentSplitterTest.pdf(
   "COMMERCIAL INVOICE No. SYN-11", "Invoice No. SYN-11 Page 1 of 1"),null);
  assertThat(proposal.parts()).hasSize(2);
 }
}
