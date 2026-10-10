package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** The profile decides rendering resolution and binarization; stored positions stay in the 200-DPI evidence raster. */
class ScanProfileExtractionTest {
 static class FakeOcr extends DocumentExtractionService {
  final List<List<String>> commands=new ArrayList<>();
  @Override void runOcrStep(ProcessBuilder builder,long seconds)throws java.io.IOException{
   var args=builder.command();commands.add(args);
   if(args.get(0).equals("pdftoppm")){
    java.nio.file.Files.write(java.nio.file.Path.of(args.get(args.size()-1)+"-1.png"),new byte[]{1});
   }else{
    java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".txt"),"Synthetic page");
    java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)+".tsv"),"5\t1\t1\t1\t1\t1\t400\t800\t200\t40\t99\tSynthetic\n");
   }
  }
 }
 private static de.ostms.lc.document.domain.LcDocument document()throws Exception{
  var doc=new de.ostms.lc.document.domain.LcDocument();doc.setContentType("application/pdf");doc.setOriginalFilename("scan.pdf");doc.setContent(PdfDocumentSplitterTest.pdf(""));return doc;
 }
 private static FakeOcr withProfile(ScanProfile profile){
  var service=new FakeOcr();var profiles=Mockito.mock(ScanProfileService.class);Mockito.when(profiles.current()).thenReturn(profile);
  ReflectionTestUtils.setField(service,"scanProfiles",profiles);return service;
 }
 private static String dpiOf(List<String> pdftoppm){return pdftoppm.get(pdftoppm.indexOf("-r")+1);}

 @Test void withoutAProfileServiceTheStandardBehaviourApplies()throws Exception{
  var service=new FakeOcr();var doc=document();service.extractInBackground(doc);
  assertThat(dpiOf(service.commands.get(0))).isEqualTo("300");
  assertThat(service.commands.get(1)).contains("thresholding_method=2");
  var evidence=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  assertThat(evidence.method()).isEqualTo("TESSERACT_WORD_MIN_V2");assertThat(evidence.dpi()).isEqualTo(200);
  assertThat(evidence.words().get(0).left()).isEqualTo(267);assertThat(evidence.words().get(0).top()).isEqualTo(533);
 }
 @Test void aProfileScannerRendersAt300WithoutExtraBinarizationAndIsMarkedInTheEvidence()throws Exception{
  var service=withProfile(ScanProfile.PROFI_SCANNER);var doc=document();service.extractInBackground(doc);
  assertThat(dpiOf(service.commands.get(0))).isEqualTo("300");assertThat(service.commands.get(1)).doesNotContain("thresholding_method=2");
  assertThat(DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson()).method()).isEqualTo("TESSERACT_WORD_MIN_V2+PROFI_SCANNER");
 }
 @Test void aPoorScanProfileRendersAt400AndPositionsAreStillStoredInTheEvidenceRaster()throws Exception{
  var service=withProfile(ScanProfile.SCHLECHTER_SCAN);var doc=document();service.extractInBackground(doc);
  assertThat(dpiOf(service.commands.get(0))).isEqualTo("400");assertThat(service.commands.get(1)).contains("thresholding_method=2");
  var evidence=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  assertThat(evidence.dpi()).isEqualTo(200);
  assertThat(evidence.words().get(0).left()).as("400 → 200 DPI").isEqualTo(200);assertThat(evidence.words().get(0).top()).isEqualTo(400);assertThat(evidence.words().get(0).width()).isEqualTo(100);
  assertThat(evidence.method()).isEqualTo("TESSERACT_WORD_MIN_V2+SCHLECHTER_SCAN");
 }
 @Test void aBrokenProfileLookupFallsBackToStandardInsteadOfStoppingRecognition()throws Exception{
  var service=new FakeOcr();var profiles=Mockito.mock(ScanProfileService.class);Mockito.when(profiles.current()).thenThrow(new IllegalStateException("db down"));
  ReflectionTestUtils.setField(service,"scanProfiles",profiles);var doc=document();service.extractInBackground(doc);
  assertThat(dpiOf(service.commands.get(0))).isEqualTo("300");assertThat(doc.getExtractionStatus()).isEqualTo("OCR_EXTRACTED");
 }
}
