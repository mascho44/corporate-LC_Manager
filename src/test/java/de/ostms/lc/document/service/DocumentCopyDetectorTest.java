package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DocumentCopyDetectorTest {
 @Test void recognizesStandaloneStamps(){
  assertThat(DocumentCopyDetector.detect("Invoice\nORIGINAL\nPage 1").copyNumber()).isZero();
  assertThat(DocumentCopyDetector.detect("COPY 2").copyNumber()).isEqualTo(1);
  assertThat(DocumentCopyDetector.detect("3rd COPY").copyNumber()).isEqualTo(1);
  assertThat(DocumentCopyDetector.detect("KOPIE 1").copyNumber()).isEqualTo(1);
  assertThat(DocumentCopyDetector.detect("2nd ORIGINAL").copyNumber()).isEqualTo(0);
  assertThat(DocumentCopyDetector.detect("ORIGINAL 3").copyNumber()).isEqualTo(0);
  assertThat(DocumentCopyDetector.detect("FIRST ORIGINAL").copyNumber()).isEqualTo(0);
  assertThat(DocumentCopyDetector.detect("SECOND ORIGINAL").copyNumber()).isEqualTo(0);
  assertThat(DocumentCopyDetector.detect("THIRD ORIGINAL").copyNumber()).isEqualTo(0);
 }
 @Test void doesNotInventCopyNumbersOrInferFromRequirements(){
  assertThat(DocumentCopyDetector.detect("COPY").kind()).isEqualTo("COPY");
  assertThat(DocumentCopyDetector.detect("COPY").copyNumber()).isEqualTo(1);
  assertThat(DocumentCopyDetector.detect("Present one original and three copies\nCOPYRIGHT\nNON NEGOTIABLE").kind()).isEqualTo("UNKNOWN");
 }
 @Test void conflictsRequireReview(){
  assertThat(DocumentCopyDetector.detect("ORIGINAL\nCOPY 1").kind()).isEqualTo("CONFLICT");
  assertThat(DocumentCopyDetector.detect("COPY 1\nCOPY 2").copyNumber()).isEqualTo(1);
 }
 @Test void splitRetainsPageHints()throws Exception{
  var proposal=PdfDocumentSplitter.propose(PdfDocumentSplitterTest.pdf("ORIGINAL","COPY 2"),null);
  assertThat(proposal.pages().get(0).copyHint().copyNumber()).isZero();
  assertThat(proposal.parts().get(1).copyNumber()).isEqualTo(1);
 }
 @Test void separatesOriginalAndCopyOfSameDocument()throws Exception{
  byte[] content;
  try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new java.io.ByteArrayOutputStream()){
   for(String stamp:new String[]{"ORIGINAL","COPY 1"}){
    var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);
    try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){
     stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
     stream.newLineAtOffset(40,700);stream.showText("COMMERCIAL INVOICE");stream.newLineAtOffset(0,-20);stream.showText(stamp);stream.endText();
    }
   }
   pdf.save(bytes);content=bytes.toByteArray();
  }
  var proposal=PdfDocumentSplitter.propose(content,null);
  assertThat(proposal.parts()).hasSize(2);
  assertThat(proposal.parts().get(0).copyNumber()).isZero();
  assertThat(proposal.parts().get(1).copyNumber()).isEqualTo(1);
 }
}
