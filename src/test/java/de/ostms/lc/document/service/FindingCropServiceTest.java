package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.LcDocument;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FindingCropServiceTest {
 private final FindingCropService service=new FindingCropService(Mockito.mock(SignatureEvidenceService.class),Mockito.mock(PdfPagePreviewService.class));

 @Test void unknownKindHasNoRegion(){assertTrue(service.region(new LcDocument(),"stamp").isEmpty());}

 @Test void dateWithoutOcrHasNoRegion(){
  var doc=new LcDocument();doc.setDocumentDate(LocalDate.of(2025,3,12));
  assertTrue(service.region(doc,"date").isEmpty());
 }

 @Test void signatureWithoutEvidenceHasNoRegion(){
  var sig=Mockito.mock(SignatureEvidenceService.class);
  Mockito.when(sig.evidence(Mockito.any())).thenReturn(SignatureEvidenceService.Evidence.unavailable());
  assertTrue(new FindingCropService(sig,Mockito.mock(PdfPagePreviewService.class)).region(new LcDocument(),"signature").isEmpty());
 }

 @Test void signatureWithInkUnionsCaptionAndInk(){
  var sig=Mockito.mock(SignatureEvidenceService.class);
  var anchor=new SignatureDetector.Anchor("Signature",new SignatureDetector.Box(100,500,200,30),true,new SignatureDetector.Box(120,440,150,60));
  Mockito.when(sig.evidence(Mockito.any())).thenReturn(new SignatureEvidenceService.Evidence(true,List.of(new SignatureDetector.PageResult(2,List.of(anchor),0,List.of()))));
  var field=new FindingCropService(sig,Mockito.mock(PdfPagePreviewService.class)).region(new LcDocument(),"signature").orElseThrow();
  assertEquals(2,field.page());assertEquals(100,field.left());assertEquals(440,field.top());assertEquals(200,field.width());assertEquals(90,field.height());
 }
 @Test void dateIsLocatedInOcrWords()throws Exception{
  var doc=new LcDocument();doc.setDocumentDate(LocalDate.of(2025,3,12));
  var ocr=new OcrEvidence("t","m",200,0.0,List.of(new OcrEvidence.Word("Date:",.9,1,10,10,50,20),new OcrEvidence.Word("12.03.2025",.9,3,300,400,160,24)),List.of());
  doc.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ocr));
  var field=service.region(doc,"date").orElseThrow();
  assertEquals(3,field.page());assertEquals(300,field.left());assertEquals(400,field.top());
 }
 @Test void stampIsLocatedWithItsNumber()throws Exception{
  var doc=new LcDocument();
  var ocr=new OcrEvidence("t","m",200,0.0,List.of(
   new OcrEvidence.Word("Copy",.9,1,900,700,80,24),
   new OcrEvidence.Word("ORIGINAL",.95,2,300,120,200,40),
   new OcrEvidence.Word("2",.9,2,520,122,20,38),
   new OcrEvidence.Word("Invoice",.9,2,100,400,90,24)),List.of());
  doc.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ocr));
  var field=service.region(doc,"stamp").orElseThrow();
  assertEquals(2,field.page());assertEquals(300,field.left());assertEquals(120,field.top());assertEquals(240,field.width());
 }
 @Test void stampWithoutKindWordHasNoRegion()throws Exception{
  var doc=new LcDocument();
  var ocr=new OcrEvidence("t","m",200,0.0,List.of(new OcrEvidence.Word("Invoice",.9,1,10,10,50,20)),List.of());
  doc.setOcrEvidenceJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ocr));
  assertTrue(service.region(doc,"stamp").isEmpty());
 }
}
