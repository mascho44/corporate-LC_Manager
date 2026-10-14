package de.ostms.lc.document.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScannedPageTest {
 /** One page per entry: the text layer (may be null) and whether a large scan image lies on it. */
 static byte[] pdf(String[] texts,boolean[] images)throws Exception{
  try(var document=new PDDocument();var out=new ByteArrayOutputStream()){
   var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
   for(int i=0;i<texts.length;i++){
    var page=new PDPage();document.addPage(page);
    try(var stream=new PDPageContentStream(document,page)){
     if(images[i])stream.drawImage(LosslessFactory.createFromImage(document,new BufferedImage(600,800,BufferedImage.TYPE_INT_RGB)),0,0,500,700);
     if(texts[i]!=null){stream.beginText();stream.setFont(font,12);stream.newLineAtOffset(50,780);stream.showText(texts[i]);stream.endText();}
    }
   }
   document.save(out);return out.toByteArray();
  }
 }

 /** A one-page scan: the given image full page plus a stamp as the only text. */
 static byte[] pdfWithImage(BufferedImage image,String stamp)throws Exception{
  try(var document=new PDDocument();var out=new ByteArrayOutputStream()){
   var page=new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);document.addPage(page);
   try(var stream=new PDPageContentStream(document,page)){
    stream.drawImage(LosslessFactory.createFromImage(document,image),0,0,595,842);
    stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),8);stream.newLineAtOffset(500,820);stream.showText(stamp);stream.endText();
   }
   document.save(out);return out.toByteArray();
  }
 }

 @Test void aScanWithOnlyAStampInItsTextLayerIsTreatedAsAScanButShortDigitalPagesKeepTheirText()throws Exception{
  var content=pdf(new String[]{"1+2K","COMMERCIAL INVOICE",null,"Eine ganz normale digitale Seite mit genug Text darauf, um keine Texterkennung zu brauchen."},new boolean[]{true,false,true,true});
  var pages=DocumentExtractionService.readPdfPages(content);
  assertThat(pages.get(0)).isEmpty();                       // stamp only + big image: scan
  assertThat(pages.get(1)).contains("COMMERCIAL INVOICE");  // short but digital (no image)
  assertThat(pages.get(2)).isEmpty();                       // no text at all
  assertThat(pages.get(3)).contains("normale digitale Seite"); // enough text, even with an image
  assertThat(DocumentExtractionService.blankPageRanges(pages)).hasSize(2);
 }

 @Test void splitUsesTheRecognisedTextOfAScanInsteadOfTheStrayLayerText()throws Exception{
  var content=pdf(new String[]{"1+2K"},new boolean[]{true});
  var evidence=new OcrEvidence("t","PDF_TEXT_POSITIONS+OCR",200,0.8,List.of(),List.of(new OcrEvidence.PageResult(1,"OCR_EXTRACTED",1,0,"COMMERCIAL INVOICE No. 13502600 Total 70.831,40 EUR")));
  var json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence);
  assertThat(PdfDocumentSplitter.texts(content,json).get(0)).contains("COMMERCIAL INVOICE").contains("70.831,40");
  assertThat(PdfDocumentSplitter.texts(content,null).get(0)).contains("1+2K");
 }
}
