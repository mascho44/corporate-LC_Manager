package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;
class OcrPipelineIntegrationTest {
 @Test void capturesRealTesseractWordsForImageOnlyPdf()throws Exception{
  boolean available;
  try{var process=new ProcessBuilder("tesseract","--version").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();available=process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)&&process.exitValue()==0;if(process.isAlive())process.destroyForcibly();}catch(java.io.IOException e){available=false;}
  if("true".equalsIgnoreCase(System.getenv("CI")))assertThat(available).as("CI must provide real Tesseract").isTrue();
  Assumptions.assumeTrue(available,"Real OCR integration requires installed Tesseract");
  var image=new BufferedImage(1200,1600,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1200,1600);g.setColor(Color.BLACK);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,45));g.drawString(":20:LC123456",80,180);g.drawString(":32B:EUR1000,00",80,280);g.dispose();
  byte[] content;
  try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){var page=new PDPage();pdf.addPage(page);try(var stream=new PDPageContentStream(pdf,page)){stream.drawImage(LosslessFactory.createFromImage(pdf,image),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}pdf.save(out);content=out.toByteArray();}
  var result=new DocumentExtractionService().extractFile(content,"scan.pdf","application/pdf");
  assertThat(result.status()).isEqualTo("OCR_EXTRACTED");assertThat(result.ocrEvidence()).isNotNull();assertThat(result.ocrEvidence().words()).isNotEmpty();assertThat(result.ocrEvidence().engineVersion()).containsIgnoringCase("tesseract");assertThat(result.ocrEvidence().words()).allMatch(w->w.page()==1);
  // This integration test verifies measured evidence, not perfect glyph recognition.
  // Assess an actual, unique OCR token; specific expected references belong in accuracy benchmarks.
  var tokens=result.ocrEvidence().words().stream().map(w->w.text().replaceAll("\\s+","").replaceFirst("^:\\d{2}[A-Z]?:","")).filter(token->!token.isEmpty()).toList();
  String unique=tokens.stream().filter(token->java.util.Collections.frequency(tokens,token)==1).findFirst().orElseThrow();
  assertThat(result.ocrEvidence().assess(unique,.8).score()).isNotNull();
  assertThat(result.ocrEvidence().assess("SYNTHETIC_VALUE_NOT_IN_SCAN",.8).score()).isNull();
  try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){
   var digital=new PDPage();pdf.addPage(digital);
   try(var stream=new PDPageContentStream(pdf,digital)){stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(30,700);stream.showText("SYNTHETIC DIGITAL COVER");stream.endText();}
   var scan=new PDPage();pdf.addPage(scan);try(var stream=new PDPageContentStream(pdf,scan)){stream.drawImage(LosslessFactory.createFromImage(pdf,image),0,0,scan.getMediaBox().getWidth(),scan.getMediaBox().getHeight());}
   pdf.save(out);var mixed=new DocumentExtractionService().extractFile(out.toByteArray(),"mixed.pdf","application/pdf");
   assertThat(mixed.status()).isEqualTo("OCR_EXTRACTED");assertThat(mixed.text()).contains("SYNTHETIC DIGITAL COVER","LC123456");
   assertThat(mixed.ocrEvidence().words()).isNotEmpty();
   assertThat(mixed.ocrEvidence().words().stream().filter(w->w.confidence()!=null)).isNotEmpty().allMatch(w->w.page()==2);
   assertThat(mixed.ocrEvidence().words().stream().filter(w->w.confidence()==null)).isNotEmpty().allMatch(w->w.page()==1);
  }
 }
}
