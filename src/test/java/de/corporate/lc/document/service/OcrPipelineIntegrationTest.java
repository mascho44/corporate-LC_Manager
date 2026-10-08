package de.corporate.lc.document.service;
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
  Assumptions.assumeTrue(available,"Real OCR integration requires installed Tesseract");
  var image=new BufferedImage(1200,1600,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1200,1600);g.setColor(Color.BLACK);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,45));g.drawString(":20:LC123456",80,180);g.drawString(":32B:EUR1000,00",80,280);g.dispose();
  byte[] content;
  try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){var page=new PDPage();pdf.addPage(page);try(var stream=new PDPageContentStream(pdf,page)){stream.drawImage(LosslessFactory.createFromImage(pdf,image),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}pdf.save(out);content=out.toByteArray();}
  var result=new DocumentExtractionService().extractFile(content,"scan.pdf","application/pdf");
  assertThat(result.status()).isEqualTo("OCR_EXTRACTED");assertThat(result.ocrEvidence()).isNotNull();assertThat(result.ocrEvidence().words()).isNotEmpty();assertThat(result.ocrEvidence().engineVersion()).containsIgnoringCase("tesseract");assertThat(result.ocrEvidence().words()).allMatch(w->w.page()==1);
  assertThat(result.ocrEvidence().assess("LC123456",.8).score()).isNotNull();
  try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){
   var digital=new PDPage();pdf.addPage(digital);
   try(var stream=new PDPageContentStream(pdf,digital)){stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(30,700);stream.showText("SYNTHETIC DIGITAL COVER");stream.endText();}
   var scan=new PDPage();pdf.addPage(scan);try(var stream=new PDPageContentStream(pdf,scan)){stream.drawImage(LosslessFactory.createFromImage(pdf,image),0,0,scan.getMediaBox().getWidth(),scan.getMediaBox().getHeight());}
   pdf.save(out);var mixed=new DocumentExtractionService().extractFile(out.toByteArray(),"mixed.pdf","application/pdf");
   assertThat(mixed.status()).isEqualTo("OCR_EXTRACTED");assertThat(mixed.text()).contains("SYNTHETIC DIGITAL COVER","LC123456");
   assertThat(mixed.ocrEvidence().words()).isNotEmpty().allMatch(w->w.page()==2);
  }
 }
}
