package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.LcDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SignatureEvidenceServiceTest {
 private static byte[] pdf(boolean signed)throws Exception{
  try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
   var page=new PDPage(PDRectangle.A4);doc.addPage(page);
   try(var cs=new PDPageContentStream(doc,page)){
    cs.beginText();cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);cs.newLineAtOffset(100,120);cs.showText("Signature");cs.endText();
    cs.setLineWidth(1);cs.moveTo(95,135);cs.lineTo(300,135);cs.stroke();
    if(signed){cs.setLineWidth(2.2f);cs.moveTo(105,160);cs.curveTo(130,215,150,120,175,190);cs.curveTo(200,235,220,140,250,185);cs.lineTo(280,150);cs.stroke();}
   }
   doc.save(out);return out.toByteArray();
  }
 }
 private static LcDocument document(byte[] content){
  var doc=new LcDocument();doc.setContentType("application/pdf");doc.setContent(content);
  // 200 DPI raster: "Signature" printed at x=100pt, 120pt above the page bottom.
  int left=(int)Math.round(100*200/72.0),top=(int)Math.round((842-120-12)*200/72.0);
  var word=new OcrEvidence.Word("Signature",.96,1,left,top,150,34);
  doc.setOcrEvidenceJson(toJson(new OcrEvidence("test","TEST",200,.8,List.of(word))));return doc;
 }
 private static String toJson(OcrEvidence evidence){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence);}catch(Exception e){throw new IllegalStateException(e);}}

 @Test void signedPdfShowsInkNextToTheCaptionAndBlankPdfDoesNot()throws Exception{
  var service=new SignatureEvidenceService();
  var signed=service.evidence(document(pdf(true)));
  assertThat(signed.available()).isTrue();assertThat(signed.anyInk()).isTrue();
  var blank=service.evidence(document(pdf(false)));
  assertThat(blank.available()).isTrue();assertThat(blank.anyCaption()).isTrue();assertThat(blank.anyInk()).isFalse();assertThat(blank.anyEmptyCaption()).isTrue();
 }
 @Test void documentsWithoutEvidenceAreNotJudged(){
  var doc=new LcDocument();doc.setContentType("application/pdf");doc.setContent(new byte[]{1,2,3});
  assertThat(new SignatureEvidenceService().evidence(doc).available()).isFalse();
 }
}
